/*
 * Copyright 2021 Google Inc.
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
package com.google.j2cl.transpiler.backend.kotlin;

import com.google.j2cl.common.InternalCompilerError;
import com.google.j2cl.transpiler.ast.AbstractVisitor;
import com.google.j2cl.transpiler.ast.AssertStatement;
import com.google.j2cl.transpiler.ast.Block;
import com.google.j2cl.transpiler.ast.BreakStatement;
import com.google.j2cl.transpiler.ast.CatchClause;
import com.google.j2cl.transpiler.ast.ContinueStatement;
import com.google.j2cl.transpiler.ast.DoWhileStatement;
import com.google.j2cl.transpiler.ast.Expression;
import com.google.j2cl.transpiler.ast.ExpressionStatement;
import com.google.j2cl.transpiler.ast.FieldDeclarationStatement;
import com.google.j2cl.transpiler.ast.ForStatement;
import com.google.j2cl.transpiler.ast.IfStatement;
import com.google.j2cl.transpiler.ast.LabeledStatement;
import com.google.j2cl.transpiler.ast.Node;
import com.google.j2cl.transpiler.ast.ReturnStatement;
import com.google.j2cl.transpiler.ast.Statement;
import com.google.j2cl.transpiler.ast.SwitchCase;
import com.google.j2cl.transpiler.ast.SwitchStatement;
import com.google.j2cl.transpiler.ast.WhileStatement;
import com.google.j2cl.transpiler.backend.common.SourceBuilder;
import java.util.Collection;

/** Transforms Statements to Kotlin source strings. */
public class StatementTranspiler {
  SourceBuilder builder;

  public StatementTranspiler(SourceBuilder builder) {
    this.builder = builder;
  }

  public void renderStatements(Collection<Statement> statements) {
    statements.forEach(
        s -> {
          builder.newLine();
          renderStatement(s);
        });
  }

  public void renderStatement(Statement statement) {
    class SourceTransformer extends AbstractVisitor {
      private void render(Node node) {
        node.accept(this);
      }

      @Override
      public boolean enterAssertStatement(AssertStatement assertStatement) {
        throw new InternalCompilerError("AssertStatement should have been normalized away.");
      }

      @Override
      public boolean enterBlock(Block block) {
        builder.openBrace();
        renderStatements(block.getStatements());
        builder.closeBrace();
        return false;
      }

      @Override
      public boolean enterBreakStatement(BreakStatement breakStatement) {
        builder.append("break");
        return false;
      }

      @Override
      public boolean enterCatchClause(CatchClause catchClause) {
        // TODO(dpo): Add support for exceptions.
        throw new InternalCompilerError("Exceptions not currently supported.");
      }

      @Override
      public boolean enterContinueStatement(ContinueStatement continueStatement) {
        builder.append("continue");
        return false;
      }

      @Override
      public boolean enterDoWhileStatement(DoWhileStatement doWhileStatement) {
        builder.append("do ");
        render(doWhileStatement.getBody());
        builder.append("while (");
        renderExpression(doWhileStatement.getConditionExpression());
        builder.append(")");
        return false;
      }

      @Override
      public boolean enterExpressionStatement(ExpressionStatement expressionStatement) {
        renderExpression(expressionStatement.getExpression());
        return false;
      }

      @Override
      public boolean enterForStatement(ForStatement forStatement) {
         throw new InternalCompilerError("ForStatement should have been normalized away.");
      }

      @Override
      public boolean enterIfStatement(IfStatement ifStatement) {
        builder.append("if (");
        renderExpression(ifStatement.getConditionExpression());
        builder.append(") ");
        render(ifStatement.getThenStatement());
        if (ifStatement.getElseStatement() != null) {
          builder.append(" else ");
          render(ifStatement.getElseStatement());
        }
        return false;
      }

      @Override
      public boolean enterFieldDeclarationStatement(FieldDeclarationStatement declaration) {
        builder.append("var ");
        builder.append(declaration.getFieldDescriptor().getName());
        builder.append(": ");
        builder.append(
            declaration.getFieldDescriptor().getTypeDescriptor().getReadableDescription());
        builder.append(" = ");
        renderExpression(declaration.getExpression());
        return false;
      }

      @Override
      public boolean enterLabeledStatement(LabeledStatement labelStatement) {
        builder.append(labelStatement.getLabel().getName() + "@ ");
        Statement innerStatement = labelStatement.getStatement();
        if (innerStatement instanceof LabeledStatement) {
          builder.openBrace();
        }
        render(innerStatement);
        if (innerStatement instanceof LabeledStatement) {
          builder.closeBrace();
        }
        return false;
      }

      @Override
      public boolean enterReturnStatement(ReturnStatement returnStatement) {
        Expression expression = returnStatement.getExpression();
        builder.append("return");
        if (expression != null) {
          builder.append(" ");
          renderExpression(expression);
        } else {
          builder.append(" Unit");
        }
        return false;
      }

      @Override
      public boolean enterSwitchCase(SwitchCase switchCase) {
        if (switchCase.isDefault()) {
          builder.append("else -> ");
        } else {
          renderExpression(switchCase.getCaseExpression());
          builder.append(" -> ");
        }
        builder.indent();
        renderStatements(switchCase.getStatements());
        builder.unindent();
        return false;
      }

      @Override
      public boolean enterSwitchStatement(SwitchStatement switchStatement) {
        // Note: assumes Java Switch-Case statements have been transformed into Kotlin
        // When-statements.
        builder.append("when (");
        renderExpression(switchStatement.getSwitchExpression());
        builder.append(") ");
        builder.openBrace();
        for (SwitchCase switchcase : switchStatement.getCases()) {
          builder.newLine();
          render(switchcase);
        }
        builder.closeBrace();
        return false;
      }

      @Override
      public boolean enterWhileStatement(WhileStatement whileStatement) {
        builder.append("while (");
        renderExpression(whileStatement.getConditionExpression());
        builder.append(") ");
        render(whileStatement.getBody());
        return false;
      }

      @Override
      public boolean enterStatement(Statement statement) {
        throw new IllegalStateException("Unhandled statement " + statement);
      }

      @Override
      public String toString() {
        return builder.build();
      }

      private void renderExpression(Expression expression) {
        ExpressionTranspiler.render(expression, builder);
      }
    }

    statement.accept(new SourceTransformer());
  }
}
