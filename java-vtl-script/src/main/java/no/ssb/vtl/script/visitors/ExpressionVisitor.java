package no.ssb.vtl.script.visitors;

/*-
 * ========================LICENSE_START=================================
 * Java VTL
 * %%
 * Copyright (C) 2016 - 2017 Hadrien Kohl
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =========================LICENSE_END==================================
 */

import no.ssb.vtl.model.Dataset;
import no.ssb.vtl.model.VTLBoolean;
import no.ssb.vtl.model.VTLExpression;
import no.ssb.vtl.model.VTLNumber;
import no.ssb.vtl.model.VTLObject;
import no.ssb.vtl.model.VTLTyped;
import no.ssb.vtl.parser.VTLBaseVisitor;
import no.ssb.vtl.parser.VTLParser;
import no.ssb.vtl.script.VTLDataset;
import no.ssb.vtl.script.error.ContextualRuntimeException;
import no.ssb.vtl.script.error.VTLRuntimeException;
import no.ssb.vtl.script.expressions.FunctionExpression;
import no.ssb.vtl.script.expressions.IfThenElseExpression;
import no.ssb.vtl.script.expressions.LiteralExpression;
import no.ssb.vtl.script.expressions.arithmetic.AdditionExpression;
import no.ssb.vtl.script.expressions.arithmetic.DivisionExpression;
import no.ssb.vtl.script.expressions.arithmetic.MultiplicationExpression;
import no.ssb.vtl.script.expressions.arithmetic.SubtractionExpression;
import no.ssb.vtl.script.expressions.equality.EqualExpression;
import no.ssb.vtl.script.expressions.equality.GraterThanExpression;
import no.ssb.vtl.script.expressions.equality.GreaterOrEqualExpression;
import no.ssb.vtl.script.expressions.equality.LesserOrEqualExpression;
import no.ssb.vtl.script.expressions.equality.LesserThanExpression;
import no.ssb.vtl.script.expressions.equality.NotEqualExpression;
import no.ssb.vtl.script.expressions.logic.AndExpression;
import no.ssb.vtl.script.expressions.logic.OrExpression;
import no.ssb.vtl.script.expressions.logic.XorExpression;
import no.ssb.vtl.script.functions.VTLConcatenation;
import no.ssb.vtl.script.functions.VTLNot;
import no.ssb.vtl.script.visitors.functions.NativeFunctionsVisitor;
import org.antlr.v4.runtime.misc.ParseCancellationException;

import javax.script.Bindings;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.lang.String.format;

/**
 * TODO: extend abstract variable visitor.
 * TODO: Reduce complexity.
 */
public class ExpressionVisitor extends VTLBaseVisitor<VTLExpression> {

    private final LiteralVisitor literalVisitor = LiteralVisitor.getInstance();
    private final NativeFunctionsVisitor nativeFunctionsVisitor = new NativeFunctionsVisitor(this);

    public Bindings getBindings() {
        return scope;
    }

    private final Bindings scope;

    public ExpressionVisitor(Bindings scope) {
        this.scope = checkNotNull(scope);
    }

    @Override
    public VTLExpression visitLiteral(VTLParser.LiteralContext ctx) {
        VTLObject literal = literalVisitor.visit(ctx);
        return new LiteralExpression(literal);
    }

    @Override
    public VTLExpression visitPostfixExpr(VTLParser.PostfixExprContext ctx) {
        VTLExpression operand = visit(ctx.expression());
        switch (ctx.op.getType()) {
            case VTLParser.ISNOTNULL:
                return getIsNullExpression(object -> object.get() != null, operand);
            case VTLParser.ISNULL:
                return getIsNullExpression(object -> object.get() == null, operand);
            default:
                throw new ParseCancellationException("unknown operator " + ctx.op.getText());
        }
    }

    @Override
    public VTLExpression visitPrecedenceExpr(VTLParser.PrecedenceExprContext ctx) {
        return visit(ctx.expression());
    }

    @Override
    public VTLExpression visitUnaryExpr(VTLParser.UnaryExprContext ctx) {
        VTLExpression operand = visit(ctx.expression());
        switch (ctx.op.getType()) {
            case VTLParser.NOT:
                return new FunctionExpression<>(VTLNot.getInstance(), operand);
            default:
                throw new ParseCancellationException("unknown operator " + ctx.op.getText());
        }
    }

    private static boolean isNull(VTLTyped typed) {
        return VTLObject.class.equals(typed.getVTLType());
    }

    @Override
    public VTLExpression visitArithmeticExpr(VTLParser.ArithmeticExprContext ctx) {

        // Check that the operands are of type number.
        VTLExpression leftExpression = visit(ctx.left);
        if (isNull(leftExpression) || !VTLNumber.class.isAssignableFrom(leftExpression.getVTLType())) {
            throw new ContextualRuntimeException(format("%s was not a numeric expression", ctx.left.getText()), ctx.left);
        }

        VTLExpression rightExpression = visit(ctx.right);
        if (isNull(rightExpression) || !VTLNumber.class.isAssignableFrom(rightExpression.getVTLType())) {
            throw new ContextualRuntimeException(format("%s was not a numeric expression", ctx.right.getText()), ctx.right);
        }

        switch (ctx.op.getType()) {
            case VTLParser.MUL:
                return new MultiplicationExpression(leftExpression, rightExpression);
            case VTLParser.DIV:
                return new DivisionExpression(leftExpression, rightExpression);
            case VTLParser.PLUS:
                return new AdditionExpression(leftExpression, rightExpression);
            case VTLParser.MINUS:
                return new SubtractionExpression(leftExpression, rightExpression);
            default:
                throw new ParseCancellationException("unknown operator " + ctx.op.getText());
        }
    }

    @Override
    public VTLExpression visitBinaryExpr(VTLParser.BinaryExprContext ctx) {
        VTLExpression leftExpression = visit(ctx.left);
        VTLExpression rightExpression = visit(ctx.right);
        switch (ctx.op.getType()) {

            case VTLParser.CONCAT:
                return new FunctionExpression<>(VTLConcatenation.getInstance(), leftExpression, rightExpression);

            case VTLParser.EQ:
            case VTLParser.NE:
            case VTLParser.LE:
            case VTLParser.LT:
            case VTLParser.GE:
            case VTLParser.GT:
                return getEqualityExpression(ctx);

            case VTLParser.AND:
            case VTLParser.OR:
            case VTLParser.XOR:
                return getBooleanExpression(ctx);

            default:
                throw new ParseCancellationException("unknown operator " + ctx.op.getText());
        }
    }

    private VTLExpression getEqualityExpression(VTLParser.BinaryExprContext ctx) {
        VTLExpression leftExpression = visit(ctx.left);
        VTLExpression rightExpression = visit(ctx.right);
        switch (ctx.op.getType()) {
            case VTLParser.EQ:
                return new EqualExpression(leftExpression, rightExpression);
            case VTLParser.NE:
                return new NotEqualExpression(leftExpression, rightExpression);
            case VTLParser.LE:
                return new LesserOrEqualExpression(leftExpression, rightExpression);
            case VTLParser.LT:
                return new LesserThanExpression(leftExpression, rightExpression);
            case VTLParser.GE:
                return new GreaterOrEqualExpression(leftExpression, rightExpression);
            case VTLParser.GT:
                return new GraterThanExpression(leftExpression, rightExpression);
            default:
                throw new ParseCancellationException("unknown equality operator " + ctx.op.getText());
        }
    }

    private VTLExpression getBooleanExpression(VTLParser.BinaryExprContext ctx) {

        // Check that the operands are of type boolean.
        VTLExpression leftExpression = visit(ctx.left);
        if (!isNull(leftExpression) && !VTLBoolean.class.isAssignableFrom(leftExpression.getVTLType())) {
            throw new ContextualRuntimeException(format("%s was not a boolean expression", ctx.left.getText()), ctx.left);
        }

        VTLExpression rightExpression = visit(ctx.right);
        if (!isNull(rightExpression) && !VTLBoolean.class.isAssignableFrom(rightExpression.getVTLType())) {
            throw new ContextualRuntimeException(format("%s was not a boolean expression", ctx.right.getText()), ctx.right);
        }

        switch (ctx.op.getType()) {
            case VTLParser.AND:
                return new AndExpression(leftExpression, rightExpression);
            case VTLParser.OR:
                return new OrExpression(leftExpression, rightExpression);
            case VTLParser.XOR:
                return new XorExpression(leftExpression, rightExpression);
            default:
                throw new ParseCancellationException("unknown logic operator " + ctx.op.getText());
        }
    }

    private VTLExpression getIsNullExpression(Predicate<VTLObject> predicate, VTLExpression expression) {
        return new VTLExpression() {
            @Override
            public VTLObject resolve(Bindings bindings) {
                VTLObject object = expression.resolve(bindings);
                return VTLBoolean.of(predicate.test(object));
            }

            @Override
            public Class getVTLType() {
                return VTLBoolean.class;
            }
        };
    }

    private VTLExpression getBooleanExpression(BiPredicate<VTLObject, VTLObject> predicate, VTLExpression leftExpression, VTLExpression rightExpression) {
        return new VTLExpression() {
            @Override
            public VTLObject resolve(Bindings bindings) {
                VTLObject left = leftExpression.resolve(bindings);
                VTLObject right = rightExpression.resolve(bindings);
                return VTLBoolean.of(predicate.test(left, right));
            }

            @Override
            public Class getVTLType() {
                return VTLBoolean.class;
            }

        };
    }

    @Override
    public VTLExpression visitNvlFunction(VTLParser.NvlFunctionContext ctx) {
        return nativeFunctionsVisitor.visit(ctx);
    }

    @Override
    public VTLExpression visitNativeFunctionCall(VTLParser.NativeFunctionCallContext ctx) {
        return nativeFunctionsVisitor.visit(ctx);
    }

    @Override
    public VTLExpression visitUserFunctionCall(VTLParser.UserFunctionCallContext ctx) {
        throw new IllegalArgumentException("undefined function " +  ctx.functionName.getText());
    }

    @Override
    public VTLExpression visitMembershipExpression(VTLParser.MembershipExpressionContext ctx) {
        String leftIdentifier = checkVariableExist(scope, ctx.left);
        Object object = scope.get(leftIdentifier);
        if (object instanceof Bindings) {
            Bindings bindings = (Bindings) object;
            String rightIdentifier = checkVariableExist(bindings, ctx.right);
            VTLTyped typed = (VTLTyped) bindings.get(rightIdentifier);
            return new VTLExpression() {

                @Override
                public VTLObject resolve(Bindings bindings) {
                    return (VTLObject) ((Bindings) bindings.get(leftIdentifier)).get(rightIdentifier);
                }

                @Override
                public Class getVTLType() {
                    return typed.getVTLType();
                }

            };
        } else {
            throw new UnsupportedOperationException("[" + leftIdentifier + "] was not a dataset");
        }
    }

    private static String checkVariableExist(Bindings bindings, VTLParser.VariableContext ctx) {
        String identifier = ctx.getText();
        // TODO: Remove escape logic.
        identifier = unEscape(identifier);

        if (bindings.containsKey(identifier))
            return identifier;

        throw new ContextualRuntimeException(
                format("undefined variable %s", identifier),
                ctx
        );
    }

    @Override
    public VTLExpression visitVariable(VTLParser.VariableContext ctx) {
        String identifier = checkVariableExist(scope, ctx);
        Object object = scope.get(identifier);
        if (object instanceof VTLTyped) {

            // Save the type and identifier.
            // TODO: VariableReference extends VTLExpression2 ?
            VTLTyped typed = (VTLTyped) object;
            return new VTLExpression() {

                @Override
                public Class<?> getVTLType() {
                    return typed.getVTLType();
                }

                @Override
                public VTLObject resolve(Bindings bindings) {
                    return (VTLObject) bindings.get(identifier);
                }
            };

        }
        if (object instanceof Dataset) {
            return new VTLExpression() {
                @Override
                public VTLObject resolve(Bindings bindings) {
                    return VTLDataset.of((Dataset) bindings.get(identifier));
                }

                @Override
                public Class getVTLType() {
                    return VTLDataset.class;
                }
            };
        }
        throw new VTLRuntimeException(
                format("unknown object [%s]", object), "VTL-101", ctx
        );
    }

    @Override
    public VTLExpression visitIfThenElseExpression(VTLParser.IfThenElseExpressionContext ctx) {
        IfThenElseExpression.Builder builder = new IfThenElseExpression.Builder(visit(ctx.ifBodyExpression()));

        ctx.ifBody().forEach(ifBody -> {
            VTLExpression condition = visit(ifBody.ifBodyExpression(0));
            VTLExpression value = visit(ifBody.ifBodyExpression(1));
            builder.addCondition(condition, value);
        });

        return builder.build();
    }

    private static String unEscape(String identifier) {
        // Unescape.
        if (identifier.startsWith("\'") && identifier.endsWith("\'")) {
            identifier = identifier.substring(1, identifier.length() - 1);
        }
        return identifier;
    }
}
