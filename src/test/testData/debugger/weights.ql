fun pick() {
    return "F" by "X"
}
weights inner =
    | 50% "A" by "X"
    | 50% "B" by "X"
weights w =
    | 30% inner
    | 70% "C" by "X"
weights partial =
    | 20% "D" by "X"
    | 3x "E" by "X"
weights funcs =
    | 50% pick()
    | 50% "G" by "X"
int done = 1
