package com.yandex.yatagan.validation.impl

internal object CharSequenceComparator : Comparator<CharSequence> {
    override fun compare(one: CharSequence, another: CharSequence): Int {
        one.length.compareTo(another.length).let { if (it != 0) return it }
        for (index in one.indices) {
            one[index].compareTo(another[index]).let { if (it != 0) return it }
        }
        return 0
    }
}

internal object PathComparator : Comparator<List<CharSequence>> {
    override fun compare(one: List<CharSequence>, another: List<CharSequence>): Int {
        one.size.compareTo(another.size).let { if (it != 0) return it }
        for (index in one.indices.reversed()) {  // reversed is speculative optimization here
            CharSequenceComparator.compare(one[index], another[index]).let { if (it != 0) return it }
        }
        return 0
    }
}
