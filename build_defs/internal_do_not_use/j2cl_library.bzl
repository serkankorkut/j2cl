"""j2cl_library build macro

Takes Java source, translates it into Closure style JS and surfaces it to the
rest of the build tree with a js_common.provider. Generally library rules dep on
other library rules for reference resolution and this build macro is no
exception. In particular the deps this rule needs for reference resolution are
java_library() targets which will have been created by other invocations of
this same j2cl_library build macro.


Example use:

# Effectively creates closure_js_library(name="Foo") containing translated JS.
j2cl_library(
    name = "Foo",
    srcs = glob(["Foo.java"]),
    deps = [":Bar"]  # Directly depends on j2cl_library(name="Bar")
)

# Effectively creates closure_js_library(name="Bar") containing the results.
j2cl_library(
    name = "Bar",
    srcs = glob(["Bar.java"]),
)

"""

load(":j2cl_java_library.bzl", j2cl_library_rule = "j2cl_library")
load(":j2wasm_library.bzl", "J2WASM_LIB_ATTRS", "j2wasm_library")
load(":j2wasm_common.bzl", "j2wasm_common")
load(":j2cl_library_build_test.bzl", "build_test")
load(":j2cl_common.bzl", "J2clInfo")

_J2WASM_PACKAGES = [
    "java/com/google/apps/framework/types",
    "java/com/google/async/threadsafety",
    "java/com/google/common",
    "java/com/google/devtools/staticanalysis/errorprone",
    "java/com/google/graphics/color",
    "java/com/google/gwt/corp/emul",
    "java/com/google/gwt/corp/serialization",
    "java/com/google/i18n/identifiers",
    "java/com/google/thirdparty/publicsuffix",
    "java/com/google/visualization/bigpicture/insights",
    "third_party/java/auto",
    "third_party/java/jbox2d",
    "third_party/java_src/jbox2d/jbox2dlibrary/src/main/java",
    "third_party/java/jsr250_annotations",
    "third_party/java/jsr330_inject",
    "third_party/java/re2j",
    "third_party/java_src/google_common/current",
    "third_party/java_src/j2cl",
    "third_party/java_src/jsr330_inject",
    "third_party/java_src/re2j",
]

def _tree_artifact_proxy_impl(ctx):
    return DefaultInfo(files = depset([ctx.attr.j2cl_library[J2clInfo]._private_.output_js]))

_tree_artifact_proxy = rule(
    implementation = _tree_artifact_proxy_impl,
    attrs = {"j2cl_library": attr.label()},
)

def j2cl_library(
        name,
        generate_build_test = None,
        generate_j2wasm_library = None,
        **kwargs):
    """Translates Java source into JS source in a js_common.provider target.

    See j2cl_java_library.bzl#j2cl_library for the arguments.

    Implicit output targets:
      lib<name>.jar: A java archive containing the byte code.
      lib<name>-src.jar: A java archive containing the sources (source jar).
    """
    args = dict(kwargs)

    # If this is JRE itself, don't synthesize the JRE dep.
    target_name = "//" + native.package_name() + ":" + name
    if args.get("srcs") and target_name != "//jre/java:jre":
        jre = Label("//:jre", relative_to_caller_repository = False)
        args["deps"] = (args.get("deps") or []) + [jre]

    j2cl_library_rule(
        name = name,
        **args
    )

    # TODO(b/36549068): remove this workaround when tree artifacts can be
    # declared as the rule output.
    _tree_artifact_proxy(
        name = name + ".js",
        j2cl_library = ":" + name,
        visibility = ["//visibility:private"],
        tags = ["manual", "notap", "no-ide"],
    )

    if args.get("srcs") and (generate_build_test == None or generate_build_test):
        build_test(name, kwargs.get("tags", []))

    j2wasm_library_name = j2wasm_common.to_j2wasm_name(name)

    if generate_j2wasm_library == None:
        # By default refer back to allow list for implicit j2wasm target generation.
        generate_j2wasm_library = (
            not native.existing_rule(j2wasm_library_name) and
            any([p for p in _J2WASM_PACKAGES if native.package_name().startswith(p)])
        )

    if generate_j2wasm_library:
        j2wasm_args = _filter_j2wasm_attrs(dict(kwargs))

        _to_j2wasm_targets("deps", j2wasm_args)
        _to_j2wasm_targets("exports", j2wasm_args)
        j2wasm_args["tags"] = (j2wasm_args.get("tags") or []) + ["manual", "notap", "j2wasm", "no-ide"]

        j2wasm_library(
            name = j2wasm_library_name,
            **j2wasm_args
        )

_ALLOWED_ATTRS = [key for key in J2WASM_LIB_ATTRS] + ["tags", "visibility"]

def _filter_j2wasm_attrs(args):
    return {key: args[key] for key in _ALLOWED_ATTRS if key in args}

def _to_j2wasm_targets(key, args):
    labels = args.get(key)
    if not labels:
        return

    args[key] = [_to_j2wasm_target(label) for label in labels]

def _to_j2wasm_target(label):
    if type(label) == "string":
        return j2wasm_common.to_j2wasm_name(_absolute_label(label))

    # Label Object
    return label.relative(":%s" % j2wasm_common.to_j2wasm_name(label.name))

def _absolute_label(label):
    if label.startswith("//") or label.startswith("@"):
        if ":" in label:
            return label
        elif "/" in label:
            return "%s:%s" % (label, label.rsplit("/", 1)[-1])
        if not label.startswith("@"):
            fail("Unexpected label format: %s" % label)
        return "%s//:%s" % (label, label[1:])

    package_name = native.package_name()

    if label.startswith(":"):
        return "//%s%s" % (package_name, label)
    if ":" in label:
        return "//%s/%s" % (package_name, label)
    return "//%s:%s" % (package_name, label)
