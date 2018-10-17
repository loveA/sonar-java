package org.sonar.java.se.checks;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.sonar.check.Rule;
import org.sonar.java.matcher.MethodMatcher;
import org.sonar.java.matcher.TypeCriteria;
import org.sonar.java.resolve.JavaSymbol;
import org.sonar.java.se.CheckerContext;
import org.sonar.java.se.ProgramState;
import org.sonar.java.se.ProgramState.SymbolicValueSymbol;
import org.sonar.java.se.constraint.Constraint;
import org.sonar.java.se.symbolicvalues.SymbolicValue;
import org.sonar.plugins.java.api.semantic.Symbol;
import org.sonar.plugins.java.api.tree.MethodInvocationTree;
import org.sonar.plugins.java.api.tree.Tree;

@Rule(key = "S3065")
public class MinMaxRangeCheck extends SECheck {

  private static final MethodMatcher MIN_MAX_MATCHER = MethodMatcher.create()
    .typeDefinition("java.lang.Math")
    .name(name -> "min".equals(name) || "max".equals(name))
    .addParameter(TypeCriteria.anyType()).addParameter(TypeCriteria.anyType());

  private enum Operation {
    MIN, MAX;

    private static Operation op(MethodInvocationTree mit) {
      String methodName = mit.symbol().name();
      return "min".equals(methodName) ? Operation.MIN : Operation.MAX;
    }
  }

  private static class MinMaxRangeConstraint implements Constraint {

    private final Operation op;
    private final Number value;

    MinMaxRangeConstraint(MethodInvocationTree mit, Number value) {
      this.op = Operation.op(mit);
      this.value = value;
    }

    @Override
    public String toString() {
      return op.name() + "_" + value;
    }

  }

  @Override
  public ProgramState checkPreStatement(CheckerContext context, Tree syntaxNode) {
    ProgramState programState = context.getState();
    if (!syntaxNode.is(Tree.Kind.METHOD_INVOCATION)) {
      return programState;
    }
    MethodInvocationTree mit = (MethodInvocationTree) syntaxNode;
    if (!MIN_MAX_MATCHER.matches(mit)) {
      return programState;
    }
    List<SymbolicValueSymbol> args = programState.peekValuesAndSymbols(2);
    for (SymbolicValueSymbol arg : args) {
      Symbol symbol = arg.symbol();
      SymbolicValue argSV = arg.symbolicValue();
      MinMaxRangeConstraint argConstraint = programState.getConstraint(argSV, MinMaxRangeConstraint.class);
      if (argConstraint == null && isConstant(symbol)) {
        Optional<Object> constantValue = ((JavaSymbol.VariableJavaSymbol) symbol).constantValue();
        if (constantValue.isPresent()) {
          programState = context.getState().addConstraint(argSV, new MinMaxRangeConstraint(mit, (Number) constantValue.get()));
        }
      }
    }
    return programState;
  }

  @Override
  public ProgramState checkPostStatement(CheckerContext context, Tree syntaxNode) {
    ProgramState programState = context.getState();
    if (!syntaxNode.is(Tree.Kind.METHOD_INVOCATION)) {
      return programState;
    }
    MethodInvocationTree mit = (MethodInvocationTree) syntaxNode;
    if (!MIN_MAX_MATCHER.matches(mit)) {
      return programState;
    }
    ProgramState psBeforeInvocation = context.getNode().programState;

    List<MinMaxRangeConstraint> argsConstraints = psBeforeInvocation.peekValues(2).stream()
      .map(sv -> programState.getConstraint(sv, MinMaxRangeConstraint.class))
      .collect(Collectors.toList());

    MinMaxRangeConstraint arg0Constraint = argsConstraints.get(0);
    MinMaxRangeConstraint arg1Constraint = argsConstraints.get(1);
    if (arg0Constraint != null && arg1Constraint != null
      && arg0Constraint.op != arg1Constraint.op) {
      Number upperBound = arg0Constraint.op == Operation.MIN ? arg0Constraint.value : arg1Constraint.value;
      Number lowerBound = arg0Constraint.op == Operation.MAX ? arg0Constraint.value : arg1Constraint.value;
      if (((Comparable<Number>) lowerBound).compareTo(upperBound) > 0) {
        context.reportIssue(mit, this, "Ohlala");
      }
    }

    // produce results of min/max methods
    SymbolicValue minMaxResult = programState.peekValue();
    List<ProgramState> nexts = argsConstraints.stream()
      .filter(Objects::nonNull)
      .map(argConstraint -> programState.addConstraint(minMaxResult, argConstraint))
      .collect(Collectors.toList());

    if (nexts.isEmpty()) {
      return programState;
    }
    if (nexts.size() == 1) {
      // one result necessarily have previous known constraint
      context.addTransition(nexts.get(0));
      // other is unknown
      return programState;
    }
    // both constraints are propagated
    context.addTransition(nexts.get(1));
    return nexts.get(0);
  }

  private static boolean isConstant(@Nullable Symbol symbol) {
    return symbol != null && symbol.isVariableSymbol() && symbol.isStatic() && symbol.isFinal();
  }

}
