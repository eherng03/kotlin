/*
 * KOTLIN PSI SPEC TEST (POSITIVE)
 *
 * SPEC VERSION: 0.1-100
 * PLACE: expressions, constant-literals, real-literals -> paragraph 3 -> sentence 1
 * NUMBER: 3
 * DESCRIPTION: Real literals with omitted a whole-number part and an exponent mark without digits after it.
 */

val value = .0E
val value = .0e
val value = .00e-
val value = .000E+

val value = .0e
val value = .00E+
val value = .000e
val value = .0000E
val value = .0e+
val value = .00E-
val value = .000e
val value = .0000e
val value = .0E-

val value = .888888888888888888888888888888888888888888888888888888888888888888888888888888888888888888888888888888888888888888888888888888888888888888888888888888888888888888888888888888888888e+
val value = .000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000e
val value = .000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000e-
val value = .000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000e+