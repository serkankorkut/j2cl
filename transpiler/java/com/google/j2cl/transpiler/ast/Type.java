/*
 * Copyright 2015 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.google.j2cl.transpiler.ast;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.base.CaseFormat;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.j2cl.common.SourcePosition;
import com.google.j2cl.common.visitor.Context;
import com.google.j2cl.common.visitor.Processor;
import com.google.j2cl.common.visitor.Visitable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

/** A node that represents a Java Type declaration in the compilation unit. */
@Visitable
@Context
public class Type extends Node implements HasSourcePosition, HasJsNameInfo, HasReadableDescription {
  private final Visibility visibility;
  private boolean isStatic;
  private TypeDeclaration typeDeclaration;
  @Visitable List<Member> members = new ArrayList<>();
  @Visitable List<Statement> loadTimeStatements = new ArrayList<>();
  private final SourcePosition sourcePosition;
  private boolean isAbstract;
  private DeclaredTypeDescriptor superTypeDescriptor;

  public Type(
      SourcePosition sourcePosition, Visibility visibility, TypeDeclaration typeDeclaration) {
    this.sourcePosition = checkNotNull(sourcePosition);
    checkArgument(
        typeDeclaration.isInterface() || typeDeclaration.isClass() || typeDeclaration.isEnum());
    this.visibility = visibility;
    this.typeDeclaration = typeDeclaration;
    this.isAbstract = typeDeclaration.isAbstract();
    this.superTypeDescriptor = typeDeclaration.getSuperTypeDescriptor();
  }

  /**
   * Returns the TypeDescriptor for this Type parametrized by the type variables from the
   * declaration.
   */
  public DeclaredTypeDescriptor getTypeDescriptor() {
    return getDeclaration().toUnparameterizedTypeDescriptor();
  }

  public Kind getKind() {
    return typeDeclaration.getKind();
  }

  public void setStatic(boolean isStatic) {
    this.isStatic = isStatic;
  }

  public boolean isStatic() {
    return isStatic;
  }

  public boolean containsMethod(String mangledName) {
    return getMethods().stream().anyMatch(method -> method.getMangledName().equals(mangledName));
  }

  public boolean containsNonJsNativeMethods() {
    return getMethods().stream()
        .filter(Method::isNative)
        .anyMatch(method -> !method.getDescriptor().isJsMember());
  }

  public void setAbstract(boolean isAbstract) {
    this.isAbstract = isAbstract;
  }

  public boolean isAbstract() {
    return isAbstract;
  }

  public boolean isEnum() {
    return typeDeclaration.isEnum();
  }

  public boolean isEnumOrSubclass() {
    return isEnum() || (getSuperTypeDescriptor() != null && getSuperTypeDescriptor().isEnum());
  }

  public boolean isInterface() {
    return typeDeclaration.isInterface();
  }

  public boolean isClass() {
    return typeDeclaration.isClass();
  }

  public TypeDeclaration getOverlaidTypeDeclaration() {
    return typeDeclaration.getOverlaidTypeDeclaration();
  }

  public boolean isOverlayImplementation() {
    return typeDeclaration.getOverlaidTypeDeclaration() != null;
  }

  public TypeDeclaration getUnderlyingTypeDeclaration() {
    return isOverlayImplementation() ? getOverlaidTypeDeclaration() : getDeclaration();
  }

  public boolean isJsEnum() {
    return typeDeclaration.isJsEnum();
  }

  public boolean isJsFunctionInterface() {
    return typeDeclaration.isJsFunctionInterface();
  }

  public boolean isJsFunctionImplementation() {
    return typeDeclaration.isJsFunctionImplementation();
  }

  public List<Member> getMembers() {
    return members;
  }

  public void setMembers(List<Member> members) {
    this.members = members;
  }

  public void addMember(Member member) {
    members.add(checkNotNull(member));
  }

  public void addMember(int position, Member member) {
    members.add(position, checkNotNull(member));
  }

  public void addMembers(Collection<? extends Member> newMembers) {
    members.addAll(newMembers);
  }

  public ImmutableList<Field> getFields() {
    return members.stream()
        .filter(Member::isField)
        .map(Field.class::cast)
        .collect(toImmutableList());
  }

  /**
   * Since enum fields are just tracked as static final fields in Type we want to be able to
   * distinguish enum fields from static fields created in the enum body.
   */
  public ImmutableList<Field> getEnumFields() {
    return getFields().stream().filter(Field::isEnumField).collect(toImmutableList());
  }

  public ImmutableList<Method> getMethods() {
    return members.stream()
        .filter(Member::isMethod)
        .map(Method.class::cast)
        .collect(toImmutableList());
  }

  public Visibility getVisibility() {
    return visibility;
  }

  public boolean hasInstanceInitializerBlocks() {
    return members.stream()
        .filter(Predicates.not(Member::isStatic))
        .anyMatch(Member::isInitializerBlock);
  }

  public void addInstanceInitializerBlock(Block instanceInitializer) {
    members.add(
        InitializerBlock.newBuilder()
            .setBlock(instanceInitializer)
            .setSourcePosition(instanceInitializer.getSourcePosition())
            .setDescriptor(getTypeDescriptor().getInitMethodDescriptor())
            .build());
  }

  public void addStaticInitializerBlock(Block staticInitializer) {
    members.add(
        InitializerBlock.newBuilder()
            .setBlock(staticInitializer)
            .setSourcePosition(staticInitializer.getSourcePosition())
            .setDescriptor(getTypeDescriptor().getClinitMethodDescriptor())
            .build());
  }

  public void addStaticInitializerBlock(int index, Block staticInitializer) {
    members.add(
        index,
        InitializerBlock.newBuilder()
            .setBlock(staticInitializer)
            .setSourcePosition(staticInitializer.getSourcePosition())
            .setDescriptor(getTypeDescriptor().getClinitMethodDescriptor())
            .build());
  }

  public void addLoadTimeStatement(Statement loadTimeStatement) {
    loadTimeStatements.add(loadTimeStatement);
  }

  public List<Statement> getLoadTimeStatements() {
    return loadTimeStatements;
  }

  public TypeDeclaration getEnclosingTypeDeclaration() {
    return typeDeclaration.getEnclosingTypeDeclaration();
  }

  public void setSuperTypeDescriptor(DeclaredTypeDescriptor superTypeDescriptor) {
    this.superTypeDescriptor = superTypeDescriptor;
  }

  public DeclaredTypeDescriptor getSuperTypeDescriptor() {
    return superTypeDescriptor;
  }

  public List<DeclaredTypeDescriptor> getSuperInterfaceTypeDescriptors() {
    return typeDeclaration.getInterfaceTypeDescriptors();
  }

  public Stream<DeclaredTypeDescriptor> getSuperTypesStream() {
    return superTypeDescriptor == null
        ? getSuperInterfaceTypeDescriptors().stream()
        : Stream.concat(
            getSuperInterfaceTypeDescriptors().stream(), Stream.of(superTypeDescriptor));
  }

  public TypeDeclaration getDeclaration() {
    return typeDeclaration;
  }

  public ImmutableList<Field> getInstanceFields() {
    return members.stream()
        .filter(Member::isField)
        .filter(Predicates.not(Member::isStatic))
        .map(Field.class::cast)
        .collect(toImmutableList());
  }

  public ImmutableList<Member> getInstanceMembers() {
    return members.stream().filter(Predicates.not(Member::isStatic)).collect(toImmutableList());
  }

  public ImmutableList<Field> getStaticFields() {
    return members.stream()
        .filter(Member::isField)
        .filter(Member::isStatic)
        .map(Field.class::cast)
        .collect(toImmutableList());
  }

  public ImmutableList<InitializerBlock> getStaticInitializerBlocks() {
    return members.stream()
        .filter(Member::isStatic)
        .filter(Member::isInitializerBlock)
        .map(InitializerBlock.class::cast)
        .collect(toImmutableList());
  }

  public ImmutableList<InitializerBlock> getInstanceInitializerBlocks() {
    return members.stream()
        .filter(Predicates.not(Member::isStatic))
        .filter(Member::isInitializerBlock)
        .map(InitializerBlock.class::cast)
        .collect(toImmutableList());
  }

  public ImmutableList<Method> getConstructors() {
    return getMethods().stream().filter(Member::isConstructor).collect(toImmutableList());
  }

  @Override
  public String getSimpleJsName() {
    return typeDeclaration.getSimpleJsName();
  }

  @Override
  public String getJsNamespace() {
    return typeDeclaration.getJsNamespace();
  }

  @Override
  public boolean isNative() {
    return typeDeclaration.isNative();
  }

  @Override
  public SourcePosition getSourcePosition() {
    return sourcePosition;
  }

  @Override
  public String getReadableDescription() {
    return getDeclaration().getReadableDescription();
  }

  @Override
  public Node accept(Processor processor) {
    return Visitor_Type.visit(processor, this);
  }

  /** Synthesizes a getter and a holder for a lazily initialized field and returns the getter. */
  // TODO(b/182568721): this pattern should be considered size-effect free when optimizing the code.
  public MethodDescriptor synthesizeLazilyInitializedField(
      String fieldName, Expression initializationExpression) {
    TypeDescriptor expressionTypeDescriptor = initializationExpression.getTypeDescriptor();

    FieldDescriptor holderFieldDescriptor =
        getLazyFieldHolderFieldDescriptor(getTypeDescriptor(), expressionTypeDescriptor, fieldName);

    // Creates the member that will hold the value.
    addMember(
        Field.Builder.from(holderFieldDescriptor).setSourcePosition(SourcePosition.NONE).build());

    MethodDescriptor lazyFieldGetter =
        getLazyFieldGetterMethodDescriptor(
            getTypeDescriptor(), expressionTypeDescriptor, fieldName);

    // Synthesizes the getter:
    // $get<fieldName>() {
    //   if (<fieldName> == null) {
    //      <fieldName> = <initializationExpression>;
    //   }
    //   return <fieldName>;
    // }
    addMember(
        Method.newBuilder()
            .setMethodDescriptor(lazyFieldGetter)
            .addStatements(
                IfStatement.newBuilder()
                    .setConditionExpression(
                        FieldAccess.Builder.from(holderFieldDescriptor).build().infixEqualsNull())
                    .setThenStatement(
                        BinaryExpression.Builder.asAssignmentTo(holderFieldDescriptor)
                            .setRightOperand(initializationExpression)
                            .build()
                            .makeStatement(SourcePosition.NONE))
                    .setSourcePosition(SourcePosition.NONE)
                    .build(),
                ReturnStatement.newBuilder()
                    .setExpression(FieldAccess.Builder.from(holderFieldDescriptor).build())
                    .setTypeDescriptor(initializationExpression.getTypeDescriptor())
                    .setSourcePosition(SourcePosition.NONE)
                    .build())
            .setSourcePosition(SourcePosition.NONE)
            .build());

    return lazyFieldGetter;
  }

  private static FieldDescriptor getLazyFieldHolderFieldDescriptor(
      DeclaredTypeDescriptor enclosingTypeDescriptor,
      TypeDescriptor fieldTypeDescriptor,
      String name) {
    return FieldDescriptor.newBuilder()
        .setName("$" + name)
        .setTypeDescriptor(fieldTypeDescriptor)
        .setEnclosingTypeDescriptor(enclosingTypeDescriptor)
        .setStatic(true)
        .setSynthetic(true)
        .build();
  }

  private static MethodDescriptor getLazyFieldGetterMethodDescriptor(
      DeclaredTypeDescriptor enclosingTypeDescriptor,
      TypeDescriptor returnTypeDescriptor,
      String name) {
    return MethodDescriptor.newBuilder()
        .setName("$get" + CaseFormat.LOWER_CAMEL.to(CaseFormat.UPPER_CAMEL, name))
        .setReturnTypeDescriptor(returnTypeDescriptor)
        .setEnclosingTypeDescriptor(enclosingTypeDescriptor)
        .setStatic(true)
        .setSynthetic(true)
        .build();
  }
}
