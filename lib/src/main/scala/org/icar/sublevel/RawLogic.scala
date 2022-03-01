package org.icar.sublevel

case class RawGoal(id:String,pre : RawLTL,post : RawLTL)


trait RawPredicate
case class RawVar(index:Int) extends RawPredicate with RawLTL
case class RawTT() extends RawPredicate with RawLTL
case class RawFF() extends RawPredicate with RawLTL

trait RawLTL
case class RawNext(op:RawLTL) extends RawLTL
case class RawUntil(left:RawLTL, right:RawLTL) extends RawLTL
case class RawRelease(left:RawLTL, right:RawLTL) extends RawLTL
case class RawGlobally(op:RawLTL) extends RawLTL
case class RawFinally(op:RawLTL) extends RawLTL



case class RawConj[A](left:A, right:A) extends RawPredicate with RawLTL
case class RawDisj[A](left:A, right:A) extends RawPredicate with RawLTL
case class RawNeg[A](op:A) extends RawPredicate with RawLTL
case class RawImpl[A](left:A, right:A) extends RawPredicate with RawLTL
case class RawIff[A](left:A, right:A) extends RawPredicate with RawLTL



case class RawLTLFormula(success : Boolean, next_ltl : RawLTL) {
  def next(state : RawState): RawLTLFormula = compute_next(state,next_ltl)

  private def compute_next(state : RawState, formula : RawLTL) : RawLTLFormula = {

    formula match {
      case RawTT() => RawLTLFormula(true, RawTT())
      case RawNeg(RawTT()) => compute_next(state,RawFF())

      case RawFF() => RawLTLFormula(false, RawFF())
      case RawNeg(RawFF()) => compute_next(state,RawTT())

      case RawVar(i) =>
        if (state satisfies RawVar(i))
          RawLTLFormula(true, RawTT())
        else
          RawLTLFormula(false, RawFF())

      case RawNeg(RawVar(i)) =>
        if (state satisfies RawVar(i))
          RawLTLFormula(false, RawFF())
        else
          RawLTLFormula(true, RawTT())

      case RawConj(l, r) =>
        val a = l.asInstanceOf[RawLTL]
        val b = r.asInstanceOf[RawLTL]
        val next_a = compute_next(state,a)
        val next_b = compute_next(state,b)

        if (next_a.next_ltl != RawTT() && next_b.next_ltl != RawTT())
          RawLTLFormula(next_a.success && next_b.success, RawConj(next_a.next_ltl, next_b.next_ltl))

        else if (next_b.next_ltl != RawTT())
          RawLTLFormula(next_a.success && next_b.success, next_b.next_ltl)

        else if (next_a.next_ltl != RawTT())
          RawLTLFormula(next_a.success && next_b.success, next_a.next_ltl)

        else
          RawLTLFormula(next_a.success && next_b.success, RawTT())

      case RawNeg(RawConj(a, b)) => compute_next(state,RawDisj(RawNeg(a), RawNeg(b)))

      case RawDisj(l, r) =>
        val a = l.asInstanceOf[RawLTL]
        val b = r.asInstanceOf[RawLTL]
        val next_a = compute_next(state,a)
        val next_b = compute_next(state,b)

        val a_test = next_a.success
        val next_a_formula = next_a.next_ltl

        val b_test = next_b.success
        val next_b_formula = next_b.next_ltl

        if (next_a_formula != RawTT() && next_b_formula != RawTT())
          RawLTLFormula(a_test || b_test, RawDisj(next_a_formula, next_b_formula))

        //				else if (next_b_formula != RawTT())
        //					RawLTLSupervisor(a_test || b_test, next_b_formula)
        //
        //				else if (next_a_formula != RawTT())
        //					RawLTLSupervisor(a_test || b_test, next_a_formula)

        else
          RawLTLFormula(a_test || b_test, RawTT())

      case RawNeg(RawDisj(a, b)) => compute_next(state,RawConj(RawNeg(a), RawNeg(b)))

      case RawImpl(l, r) =>
        val a = l.asInstanceOf[RawLTL]
        val b = r.asInstanceOf[RawLTL]

        val next_a = compute_next(state,a)
        if (next_a.success) {
          val next_b = compute_next(state,b)
          RawLTLFormula(next_a.success && next_b.success, next_b.next_ltl)
        } else
          RawLTLFormula(true, RawImpl(l, r) )

      case RawNext(f) =>
        RawLTLFormula(true, f)

      case RawNeg(RawNext(f)) => compute_next(state,RawNext(RawNeg(f)))

      case RawUntil(a, b) =>
        val next_a = compute_next(state,a)
        val next_b = compute_next(state,b)
        val a_test = next_a.success
        val next_a_formula = next_a.next_ltl
        val b_test = next_b.success
        val next_b_formula = next_b.next_ltl

        if (b_test)
          RawLTLFormula(true, RawTT())
        else if (a_test)
          RawLTLFormula(true, RawUntil(a, b))
        else
          RawLTLFormula(false, RawFF())

      case RawNeg(RawUntil(a, b)) => compute_next(state, RawRelease(RawNeg(a),RawNeg(b)))

      case RawRelease(a, b) =>
        val next_a = compute_next(state,a)
        val next_b = compute_next(state,b)
        val a_test = next_a.success
        val next_a_formula = next_a.next_ltl
        val b_test = next_b.success
        val next_b_formula = next_b.next_ltl

        if (b_test) {
          if (a_test)
            RawLTLFormula(true, RawTT())
          else
            RawLTLFormula(true, RawRelease(a, b))
        } else
          RawLTLFormula(false,RawFF())

      case RawNeg(RawRelease(a, b)) => compute_next(state,RawNext(RawUntil(RawNeg(a),RawNeg(b))))

      case RawFinally(f) => compute_next(state,RawUntil(RawTT(),f))
      case RawNeg(RawFinally(f)) => compute_next(state,RawNeg(RawUntil(RawTT(),f)))

      case RawGlobally(f) => compute_next(state,RawNeg(RawFinally(RawNeg(f))))
      case RawNeg(RawGlobally(f)) => compute_next(state,RawFinally(RawNeg(f)))

      case RawNeg(RawNeg(f)) => compute_next(state,f.asInstanceOf[RawLTL])

      case _ => RawLTLFormula(false,RawFF())

    }

  }

}

