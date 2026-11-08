entity Box {
    int size
    string label = "box"
    Box(size)
}

Box b = new Box(3)
int[] nums = [1, 2, 3]
int size = b.size
