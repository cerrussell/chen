package io.appthreat.pysrc2cpg.memop

import io.appthreat.pythonparser.{AstVisitor, ast}
import io.appthreat.pythonparser.ast.{
  AnnAssign,
  Assert,
  Assign,
  AsyncFor,
  AsyncFunctionDef,
  AsyncWith,
  AugAssign,
  BinOp,
  BoolOp,
  Break,
  ClassDef,
  Continue,
  Delete,
  Dict,
  ErrorStatement,
  Expr,
  For,
  FormattedValue,
  FunctionDef,
  Global,
  If,
  IfExp,
  Import,
  ImportFrom,
  JoinedString,
  JoinedStringConstant,
  Lambda,
  Match,
  MatchAs,
  MatchCase,
  MatchClass,
  MatchMapping,
  MatchOr,
  MatchSequence,
  MatchSingleton,
  MatchStar,
  MatchValue,
  Module,
  NamedExpr,
  Nonlocal,
  Pass,
  Raise,
  RaiseP2,
  Return,
  Try,
  UnaryOp,
  While,
  With,
  iast,
  iexpr,
  imod,
  istmt
}
import io.appthreat.pythonparser.ast.{
  FormattedValue,
  JoinedString,
  JoinedStringConstant,
  MatchAs,
  MatchCase,
  MatchClass,
  MatchMapping,
  MatchOr,
  MatchSequence,
  MatchSingleton,
  MatchStar,
  MatchValue
}

import scala.collection.mutable

class MemoryOperationCalculator extends AstVisitor[Unit] {
  private val stack  = mutable.Stack.empty[MemoryOperation]
  val astNodeToMemOp = new AstNodeToMemoryOperationMap()
  val names          = mutable.Set.empty[String]

  private def accept(astNode: iast): Unit = {
    astNode.accept(this)
  }

  private def accept(astNodes: Iterable[iast]): Unit = {
    astNodes.foreach(accept)
  }

  private def push(memOp: MemoryOperation): Unit = {
    stack.push(memOp)
  }

  private def pop(): Unit = {
    stack.pop()
  }

  override def visit(astNode: iast): Unit = ???

  override def visit(mod: imod): Unit = ???

  override def visit(module: Module): Unit = {
    accept(module.stmts)
  }

  override def visit(stmt: istmt): Unit = ???

  override def visit(functionDef: FunctionDef): Unit = {
    push(Load)
    accept(functionDef.decorator_list)
    accept(functionDef.args)
    accept(functionDef.returns)
    pop()
    accept(functionDef.body)
  }

  override def visit(functionDef: AsyncFunctionDef): Unit = {
    push(Load)
    accept(functionDef.decorator_list)
    accept(functionDef.args)
    accept(functionDef.returns)
    pop()
    accept(functionDef.body)
  }

  override def visit(classDef: ClassDef): Unit = {
    push(Load)
    accept(classDef.decorator_list)
    accept(classDef.bases)
    accept(classDef.keywords)
    pop()
    accept(classDef.body)
  }

  override def visit(ret: Return): Unit = {
    push(Load)
    accept(ret.value)
    pop()
  }

  override def visit(delete: Delete): Unit = {
    push(Del)
    accept(delete.targets)
    pop()
  }

  override def visit(assign: Assign): Unit = {
    push(Store)
    accept(assign.targets)
    pop()
    push(Load)
    accept(assign.value)
    pop()
  }

  override def visit(annAssign: AnnAssign): Unit = {
    push(Store)
    accept(annAssign.target)
    pop()
    push(Load)
    accept(annAssign.annotation)
    accept(annAssign.value)
    pop()
  }

  override def visit(augAssign: AugAssign): Unit = {
    push(Store)
    accept(augAssign.target)
    pop()
    push(Load)
    accept(augAssign.value)
    pop()
  }

  override def visit(forStmt: For): Unit = {
    push(Store)
    accept(forStmt.target)
    pop()
    push(Load)
    accept(forStmt.iter)
    pop()
    accept(forStmt.body)
    accept(forStmt.orelse)
  }

  override def visit(forStmt: AsyncFor): Unit = {
    push(Store)
    accept(forStmt.target)
    pop()
    push(Load)
    accept(forStmt.iter)
    pop()
    accept(forStmt.body)
    accept(forStmt.orelse)
  }

  override def visit(whileStmt: While): Unit = {
    push(Load)
    accept(whileStmt.test)
    pop()
    accept(whileStmt.body)
    accept(whileStmt.orelse)
  }

  override def visit(ifStmt: If): Unit = {
    push(Load)
    accept(ifStmt.test)
    pop()
    accept(ifStmt.body)
    accept(ifStmt.orelse)
  }

  override def visit(withStmt: With): Unit = {
    accept(withStmt.items)
    accept(withStmt.body)
  }

  override def visit(withStmt: AsyncWith): Unit = {
    accept(withStmt.items)
    accept(withStmt.body)
  }

  override def visit(matchStmt: Match): Unit = {
    push(Load)
    accept(matchStmt.subject)
    accept(matchStmt.cases)
    pop()
  }

  override def visit(raise: Raise): Unit = {
    push(Load)
    accept(raise.exc)
    accept(raise.cause)
    pop()
  }

  override def visit(tryStmt: Try): Unit = {
    accept(tryStmt.body)
    accept(tryStmt.handlers)
    accept(tryStmt.orelse)
    accept(tryStmt.finalbody)
  }

  override def visit(assert: Assert): Unit = {
    push(Load)
    accept(assert.test)
    accept(assert.msg)
    pop()
  }

  override def visit(importStmt: Import): Unit = {}

  override def visit(importFrom: ImportFrom): Unit = {}

  override def visit(global: Global): Unit = {}

  override def visit(nonlocal: Nonlocal): Unit = {}

  override def visit(expr: Expr): Unit = {
    push(Load)
    expr.value.accept(this)
    pop()
  }

  override def visit(pass: Pass): Unit = {}

  override def visit(break: Break): Unit = {}

  override def visit(continue: Continue): Unit = {}

  override def visit(raise: RaiseP2): Unit = {
    push(Load)
    accept(raise.typ)
    accept(raise.inst)
    accept(raise.tback)
    pop()
  }

  override def visit(errorStatement: ErrorStatement): Unit = {}

  override def visit(expr: iexpr): Unit = ???

  override def visit(boolOp: BoolOp): Unit = {
    accept(boolOp.values)
  }

  override def visit(namedExpr: NamedExpr): Unit = {
    push(Store)
    accept(namedExpr.target)
    pop()
    accept(namedExpr.value)
  }

  override def visit(binOp: BinOp): Unit = {
    accept(binOp.left)
    accept(binOp.right)
  }

  override def visit(unaryOp: UnaryOp): Unit = {
    accept(unaryOp.operand)
  }

  override def visit(lambda: Lambda): Unit = {
    push(Load)
    accept(lambda.args)
    pop()
    accept(lambda.body)
  }

  override def visit(ifExp: IfExp): Unit = {
    accept(ifExp.test)
    accept(ifExp.body)
    accept(ifExp.orelse)
  }

  override def visit(dict: Dict): Unit = {
    accept(dict.keys.collect { case Some(key) => key })
    accept(dict.values)
  }

  override def visit(set: ast.Set): Unit = {
    accept(set.elts)
  }

  override def visit(listComp: ast.ListComp): Unit = {
    accept(listComp.elt)
    accept(listComp.generators)
  }

  override def visit(setComp: ast.SetComp): Unit = {
    accept(setComp.elt)
    accept(setComp.generators)
  }

  override def visit(dictComp: ast.DictComp): Unit = {
    accept(dictComp.key)
    accept(dictComp.value)
    accept(dictComp.generators)
  }

  override def visit(generatorExp: ast.GeneratorExp): Unit = {
    accept(generatorExp.elt)
    accept(generatorExp.generators)
  }

  override def visit(await: ast.Await): Unit = {
    accept(await.value)
  }

  override def visit(yieldExpr: ast.Yield): Unit = {
    accept(yieldExpr.value)
  }

  override def visit(yieldFrom: ast.YieldFrom): Unit = {
    accept(yieldFrom.value)
  }

  override def visit(compare: ast.Compare): Unit = {
    accept(compare.left)
    accept(compare.comparators)
  }

  override def visit(call: ast.Call): Unit = {
    assert(stack.head == Load)
    accept(call.func)
    accept(call.args)
    accept(call.keywords)
  }

  override def visit(formattedValue: FormattedValue): Unit = {
    assert(stack.head == Load)
    accept(formattedValue.value)
  }

  override def visit(joinedString: JoinedString): Unit = {
    assert(stack.head == Load)
    accept(joinedString.values)
  }

  override def visit(constant: ast.Constant): Unit = {}

  override def visit(attribute: ast.Attribute): Unit = {
    push(Load)
    accept(attribute.value)
    pop()
    astNodeToMemOp.put(attribute, stack.head)
  }

  override def visit(subscript: ast.Subscript): Unit = {
    push(Load)
    accept(subscript.value)
    accept(subscript.slice)
    pop()
    astNodeToMemOp.put(subscript, stack.head)
  }

  override def visit(starred: ast.Starred): Unit = {
    accept(starred.value)
    astNodeToMemOp.put(starred, stack.head)
  }

  override def visit(name: ast.Name): Unit = {
    astNodeToMemOp.put(name, stack.head)
    names.add(name.id)
  }

  override def visit(list: ast.List): Unit = {
    accept(list.elts)
    astNodeToMemOp.put(list, stack.head)
  }

  override def visit(tuple: ast.Tuple): Unit = {
    accept(tuple.elts)
    astNodeToMemOp.put(tuple, stack.head)
  }

  override def visit(slice: ast.Slice): Unit = {
    push(Load)
    accept(slice.lower)
    accept(slice.upper)
    accept(slice.step)
    pop()
  }

  override def visit(stringExpList: ast.StringExpList): Unit = {
    accept(stringExpList.elts)
  }

  override def visit(boolop: ast.iboolop): Unit = {}

  override def visit(and: ast.And.type): Unit = {}

  override def visit(or: ast.Or.type): Unit = {}

  override def visit(operator: ast.ioperator): Unit = {}

  override def visit(add: ast.Add.type): Unit = {}

  override def visit(sub: ast.Sub.type): Unit = {}

  override def visit(mult: ast.Mult.type): Unit = {}

  override def visit(matMult: ast.MatMult.type): Unit = {}

  override def visit(div: ast.Div.type): Unit = {}

  override def visit(mod: ast.Mod.type): Unit = {}

  override def visit(pow: ast.Pow.type): Unit = {}

  override def visit(lShift: ast.LShift.type): Unit = {}

  override def visit(rShift: ast.RShift.type): Unit = {}

  override def visit(bitOr: ast.BitOr.type): Unit = {}

  override def visit(bitXor: ast.BitXor.type): Unit = {}

  override def visit(bitAnd: ast.BitAnd.type): Unit = {}

  override def visit(floorDiv: ast.FloorDiv.type): Unit = {}

  override def visit(unaryop: ast.iunaryop): Unit = {}

  override def visit(invert: ast.Invert.type): Unit = {}

  override def visit(not: ast.Not.type): Unit = {}

  override def visit(uAdd: ast.UAdd.type): Unit = {}

  override def visit(uSub: ast.USub.type): Unit = {}

  override def visit(compop: ast.icompop): Unit = {}

  override def visit(eq: ast.Eq.type): Unit = {}

  override def visit(notEq: ast.NotEq.type): Unit = {}

  override def visit(lt: ast.Lt.type): Unit = {}

  override def visit(ltE: ast.LtE.type): Unit = {}

  override def visit(gt: ast.Gt.type): Unit = {}

  override def visit(gtE: ast.GtE.type): Unit = {}

  override def visit(is: ast.Is.type): Unit = {}

  override def visit(isNot: ast.IsNot.type): Unit = {}

  override def visit(in: ast.In.type): Unit = {}

  override def visit(notIn: ast.NotIn.type): Unit = {}

  override def visit(comprehension: ast.Comprehension): Unit = {
    assert(stack.head == Load)
    push(Store)
    accept(comprehension.target)
    pop()
    accept(comprehension.iter)
    accept(comprehension.ifs)
  }

  override def visit(exceptHandler: ast.ExceptHandler): Unit = {
    push(Load)
    accept(exceptHandler.typ)
    pop()
    accept(exceptHandler.body)
  }

  override def visit(arguments: ast.Arguments): Unit = {
    accept(arguments.posonlyargs)
    accept(arguments.args)
    accept(arguments.vararg)
    accept(arguments.kwonlyargs)
    accept(arguments.kw_defaults.collect { case Some(default) => default })
    accept(arguments.kw_arg)
    accept(arguments.defaults)
  }

  override def visit(arg: ast.Arg): Unit = {
    accept(arg.annotation)
  }

  override def visit(constant: ast.iconstant): Unit = ???

  override def visit(stringConstant: ast.StringConstant): Unit = {}

  override def visit(joinedStringConstant: JoinedStringConstant): Unit = {}

  override def visit(boolConstant: ast.BoolConstant): Unit = {}

  override def visit(intConstant: ast.IntConstant): Unit = {}

  override def visit(intConstant: ast.FloatConstant): Unit = {}

  override def visit(imaginaryConstant: ast.ImaginaryConstant): Unit = {}

  override def visit(noneConstant: ast.NoneConstant.type): Unit = {}

  override def visit(ellipsisConstant: ast.EllipsisConstant.type): Unit = {}

  override def visit(keyword: ast.Keyword): Unit = {
    assert(stack.head == Load)
    accept(keyword.value)
  }

  override def visit(alias: ast.Alias): Unit = {}

  override def visit(withItem: ast.Withitem): Unit = {
    push(Load)
    accept(withItem.context_expr)
    pop()
    push(Store)
    accept(withItem.optional_vars)
    pop()
  }

  override def visit(matchCase: MatchCase): Unit = {
    accept(matchCase.pattern)
    accept(matchCase.guard)
    accept(matchCase.body)
  }

  override def visit(matchValue: MatchValue): Unit = {
    accept(matchValue.value)
  }

  override def visit(matchSingleton: MatchSingleton): Unit = {}

  override def visit(matchSequence: MatchSequence): Unit = {
    accept(matchSequence.patterns)
  }

  override def visit(matchMapping: MatchMapping): Unit = {
    accept(matchMapping.keys)
    accept(matchMapping.patterns)
  }

  override def visit(matchClass: MatchClass): Unit = {
    accept(matchClass.cls)
    accept(matchClass.patterns)
    accept(matchClass.kwd_patterns)
  }

  override def visit(matchStar: MatchStar): Unit = {}

  override def visit(matchAs: MatchAs): Unit = {
    accept(matchAs.pattern)
  }

  override def visit(matchOr: MatchOr): Unit = {
    accept(matchOr.patterns)
  }

  override def visit(typeIgnore: ast.TypeIgnore): Unit = {}
}
