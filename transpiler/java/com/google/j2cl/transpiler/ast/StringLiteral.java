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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.collect.ImmutableList;
import com.google.j2cl.common.visitor.Processor;
import com.google.j2cl.common.visitor.Visitable;
import org.apache.commons.text.StringEscapeUtils;

/** String literal node. */
@Visitable
public class StringLiteral extends Literal {
  private final String value;

  public StringLiteral(String value) {
    this.value = checkNotNull(value);
  }

  public String getValue() {
    return value;
  }

  @Override
  public String getSourceText() {
    return "\'" + escapeJavaString(value) + "\'";
  }

  private static String escapeJavaString(String string) {
    // NOTE: StringEscapeUtils.escapeEcmaScript does not escape unprintable character 127 (delete).
    return StringEscapeUtils.escapeEcmaScript(string).replace("\u007f", "\\u007F");
  }

  public ImmutableList<NumberLiteral> toCharLiterals() {
    return value.chars().mapToObj(NumberLiteral::fromChar).collect(toImmutableList());
  }

  @Override
  public Node accept(Processor processor) {
    return Visitor_StringLiteral.visit(processor, this);
  }

  @Override
  public TypeDescriptor getTypeDescriptor() {
    return TypeDescriptors.get().javaLangString;
  }

  @Override
  public StringLiteral clone() {
    // String literals are value types do not need to actually clone.
    return this;
  }
}
