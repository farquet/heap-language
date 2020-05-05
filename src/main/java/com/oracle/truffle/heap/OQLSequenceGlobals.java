package com.oracle.truffle.heap;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.interop.*;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.heap.util.HeapLanguageUtils;
import org.graalvm.collections.Pair;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashSet;

/**
 * <p>These functions accept an array/iterator/enumeration and an expression string [or a callback function] as input.
 * These functions iterate the array/iterator/enumeration and apply the expression (or function) on each element.
 * Note that JavaScript objects are associative arrays. So, these functions may also be used with
 * arbitrary JavaScript objects.</p>
 */
interface OQLSequenceGlobals {

    /**
     * Concatenates two arrays or enumerations (i.e., returns composite enumeration).
     */
    @ExportLibrary(InteropLibrary.class)
    class Concat implements TruffleObject {

        public static final Concat INSTANCE = new Concat();

        private Concat() {}

        @ExportMessage
        static boolean isExecutable(@SuppressWarnings("unused") Concat receiver) {
            return true;
        }

        @ExportMessage
        static Object execute(@SuppressWarnings("unused") Concat receiver, Object[] arguments) throws ArityException {
            Interop.checkArity(arguments, 2);
            InteropLibrary interop = InteropLibrary.getFactory().getUncached();
            Iterator<Pair<?, ?>> i1 = Interop.intoIndexedIterator(arguments[0], interop);
            Iterator<Pair<?, ?>> i2 = Interop.intoIndexedIterator(arguments[1], interop);

            Iterator<Object> concatIterator = new Iterator<Object>() {

                @Override
                public boolean hasNext() {
                    return i1.hasNext() || i2.hasNext();
                }

                @Override
                public Object next() {
                    if (i1.hasNext()) {
                        return i1.next().getRight();
                    } else {
                        return i2.next().getRight();
                    }
                }
            };

            return Interop.wrapIterator(concatIterator);
        }

    }

    /**
     * Returns whether the given array/enumeration contains an element the given boolean expression specified in code.
     */
    @ExportLibrary(InteropLibrary.class)
    class Contains implements TruffleObject {

        public static final Contains INSTANCE = new Contains();

        private Contains() {}

        @ExportMessage
        static boolean isExecutable(@SuppressWarnings("unused") Contains receiver) {
            return true;
        }

        @ExportMessage
        static Object execute(@SuppressWarnings("unused") Contains receiver, Object[] arguments)
                throws ArityException, UnsupportedTypeException, UnsupportedMessageException
        {
            Interop.checkArity(arguments, 2);
            InteropLibrary interop = InteropLibrary.getFactory().getUncached();
            TruffleObject callback = HeapLanguage.resolveCallbackArgument(arguments[1], interop, "it", "index", "array");
            Iterator<Pair<?, ?>> it = Interop.intoIndexedIterator(arguments[0], interop);
            while (it.hasNext()) {
                Pair<?, ?> element = it.next();
                if (Interop.asBoolean(interop.execute(callback, element.getRight(), element.getLeft(), arguments[0]))) {
                    return Boolean.TRUE;
                }
            }
            return Boolean.FALSE;
        }

    }

    /**
     * Count function returns the count of elements of the input array/enumeration that satisfy the given boolean expression.
     */
    @ExportLibrary(InteropLibrary.class)
    class Count implements TruffleObject {

        public static final Count INSTANCE = new Count();

        private Count() {}

        @ExportMessage
        static boolean isExecutable(@SuppressWarnings("unused") Count receiver) {
            return true;
        }

        @ExportMessage
        static Object execute(@SuppressWarnings("unused") Count receiver, Object[] arguments)
                throws ArityException, UnsupportedTypeException, UnsupportedMessageException
        {
            if (arguments.length == 1) {
                return Length.execute(null, arguments);
            }
            Interop.checkArity(arguments, 2);
            InteropLibrary interop = InteropLibrary.getFactory().getUncached();
            TruffleObject callback = HeapLanguage.resolveCallbackArgument(arguments[1], interop, "it", "index", "array");
            Iterator<Pair<?, ?>> it = Interop.intoIndexedIterator(arguments[0], interop);
            int count = 0;
            while (it.hasNext()) {
                Pair<?, ?> element = it.next();
                if (Interop.asBoolean(interop.execute(callback, element.getRight(), element.getLeft(), arguments[0]))) {
                    count += 1;
                }
            }
            return count;
        }

    }

    /**
     * Filter function returns an array/enumeration that contains elements of the input array/enumeration that satisfy the given boolean expression.
     */
    @ExportLibrary(InteropLibrary.class)
    class Filter implements TruffleObject {

        public static final Filter INSTANCE = new Filter();

        private Filter() {}

        @ExportMessage
        static boolean isExecutable(@SuppressWarnings("unused") Filter receiver) {
            return true;
        }

        @ExportMessage
        static Object execute(@SuppressWarnings("unused") Filter receiver, Object[] arguments) throws ArityException {
            Interop.checkArity(arguments, 2);
            InteropLibrary interop = InteropLibrary.getFactory().getUncached();
            TruffleObject callback = HeapLanguage.resolveCallbackArgument(arguments[1], interop, "it", "index", "array", "result");
            Iterator<Pair<?, ?>> it = Interop.intoIndexedIterator(arguments[0], interop);

            Iterator<Object> filterIterator = new Iterator<Object>() {

                private Pair<?, ?> lastElement = null;

                private void advance() {
                    if (lastElement != null) return;    // only advance if last element consumed
                    try {
                        while (it.hasNext()) {
                            Pair<?, ?> element = it.next();
                            Object isValid = interop.execute(callback, element.getRight(), element.getLeft(), arguments[0], HeapLanguage.asGuestValue(this));
                            if (Interop.asBoolean(isValid)) {
                                lastElement = element;
                                return;
                            }
                        }
                    } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
                        throw new IllegalStateException("Cannot execute filter callback.", e);
                    }
                }

                @Override
                public boolean hasNext() {
                    advance();
                    return lastElement != null;
                }

                @Override
                public Object next() {
                    if (!hasNext()) throw new IllegalStateException("Iterator has no other items.");
                    Object item = lastElement.getRight();
                    lastElement = null;
                    return item;
                }

            };

            return Interop.wrapIterator(filterIterator);
        }

    }

    /**
     * Length function returns number of elements of an array/enumeration.
     */
    @ExportLibrary(InteropLibrary.class)
    class Length implements TruffleObject {

        public static final Length INSTANCE = new Length();

        private Length() {}

        @ExportMessage
        static boolean isExecutable(@SuppressWarnings("unused") Length receiver) {
            return true;
        }

        @ExportMessage
        static Object execute(@SuppressWarnings("unused") Length receiver, Object[] arguments) throws ArityException, UnsupportedMessageException {
            Interop.checkArity(arguments, 1);
            HeapLanguageUtils.arityCheck(1, arguments);
            InteropLibrary interop = InteropLibrary.getFactory().getUncached();
            Object data = arguments[0];
            if (data instanceof TruffleObject && interop.hasArrayElements(data)) {
                return interop.getArraySize(data);
            } else {
                Iterator<Pair<?, ?>> it = Interop.intoIndexedIterator(data, interop);
                int count = 0;
                while (it.hasNext()) {
                    count += 1; it.next();
                }
                return count;
            }
        }

    }

    /**
     * Transforms the given array/enumeration by evaluating given code on each element.
     */
    @ExportLibrary(InteropLibrary.class)
    class Map implements TruffleObject {

        public static final Map INSTANCE = new Map();

        private Map() {}

        @ExportMessage
        static boolean isExecutable(@SuppressWarnings("unused") Map receiver) {
            return true;
        }

        @ExportMessage
        static Object execute(@SuppressWarnings("unused") Map receiver, Object[] arguments) throws ArityException {
            Interop.checkArity(arguments, 2);
            InteropLibrary interop = InteropLibrary.getFactory().getUncached();
            Iterator<Pair<?, ?>> items = Interop.intoIndexedIterator(arguments[0], interop);
            TruffleObject callback = HeapLanguage.resolveCallbackArgument(arguments[1], interop, "it", "index", "array", "result");

            Iterator<Object> mappedIterator = new Iterator<Object>() {
                @Override
                public boolean hasNext() {
                    return items.hasNext();
                }

                @Override
                public Object next() {
                    try {
                        Pair<?, ?> item = items.next();
                        return interop.execute(callback, item.getRight(), item.getLeft(), arguments[0], callback);
                    } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
                        throw new RuntimeException("Cannot map values.", e);
                    }
                }
            };

            return Interop.wrapIterator(mappedIterator);
        }

    }

    /**
     * Returns the maximum element of the given array/enumeration. Optionally accepts code expression to compare
     * elements of the array. By default numerical comparison is used.
     */
    @ExportLibrary(InteropLibrary.class)
    class Max implements TruffleObject {

        public static final Max INSTANCE = new Max();

        private Max() {}

        @ExportMessage
        static boolean isExecutable(@SuppressWarnings("unused") Max receiver) {
            return true;
        }

        @ExportMessage
        static Object execute(@SuppressWarnings("unused") Max receiver, Object[] arguments)
                throws ArityException, UnsupportedTypeException, UnsupportedMessageException
        {
            Object max = null;
            InteropLibrary interop = InteropLibrary.getFactory().getUncached();
            if (arguments.length == 1) {    // numerical comparison
                Iterator<Pair<?, ?>> it = Interop.intoIndexedIterator(arguments[0], interop);
                if (it.hasNext()) max = it.next().getRight();
                while (it.hasNext()) {
                    Object element = it.next().getRight();
                    if (Interop.compareNumeric(max, element) < 0) {
                        max = element;
                    }
                }
            } else {
                Interop.checkArity(arguments, 2);
                TruffleObject callback = HeapLanguage.resolveCallbackArgument(arguments[1], interop, "lhs", "rhs");
                Iterator<Pair<?, ?>> it = Interop.intoIndexedIterator(arguments[0], interop);
                if (it.hasNext()) max = it.next().getRight();
                while (it.hasNext()) {
                    Object element = it.next().getRight();
                    if (Interop.asBoolean(interop.execute(callback, element, max))) {   // true if lhs > rhs
                        max = element;
                    }
                }
            }
            return max == null ? HeapLanguage.NULL : max;
        }

    }

    /**
     * Returns the minimum element of the given array/enumeration. Optionally accepts code expression to compare
     * elements of the array. By default numerical comparison is used.
     */
    @ExportLibrary(InteropLibrary.class)
    class Min implements TruffleObject {

        public static final Min INSTANCE = new Min();

        private Min() {}

        @ExportMessage
        static boolean isExecutable(@SuppressWarnings("unused") Min receiver) {
            return true;
        }

        @ExportMessage
        static Object execute(@SuppressWarnings("unused") Min receiver, Object[] arguments)
                throws ArityException, UnsupportedTypeException, UnsupportedMessageException
        {
            Object min = null;
            InteropLibrary interop = InteropLibrary.getFactory().getUncached();
            if (arguments.length == 1) {    // numerical comparison
                Iterator<Pair<?, ?>> it = Interop.intoIndexedIterator(arguments[0], interop);
                if (it.hasNext()) min = it.next().getRight();
                while (it.hasNext()) {
                    Object element = it.next().getRight();
                    if (Interop.compareNumeric(min, element) > 0) {
                        min = element;
                    }
                }
            } else {
                Interop.checkArity(arguments, 2);
                TruffleObject callback = HeapLanguage.resolveCallbackArgument(arguments[1], interop, "lhs", "rhs");
                Iterator<Pair<?, ?>> it = Interop.intoIndexedIterator(arguments[0], interop);
                if (it.hasNext()) min = it.next().getRight();
                while (it.hasNext()) {
                    Object element = it.next().getRight();
                    if (Interop.asBoolean(interop.execute(callback, element, min))) {   // true if lhs < rhs
                        min = element;
                    }
                }
            }
            return min == null ? HeapLanguage.NULL : min;
        }
    }

    /**
     * Sorts given array/enumeration. Optionally accepts code expression to compare elements of the array. By default
     * numerical comparison is used.
     */
    @ExportLibrary(InteropLibrary.class)
    class Sort implements TruffleObject {

        public static final Sort INSTANCE = new Sort();

        private Sort() {}

        @ExportMessage
        static boolean isExecutable(@SuppressWarnings("unused") Sort receiver) {
            return true;
        }

        @ExportMessage
        static Object execute(@SuppressWarnings("unused") Sort receiver, Object[] arguments)
                throws ArityException
        {
            Interop.checkArityOptional(arguments, 1, 2);
            InteropLibrary interop = InteropLibrary.getFactory().getUncached();
            ArrayList<Object> items = new ArrayList<>();
            Iterator<Pair<?, ?>> it = Interop.intoIndexedIterator(arguments[0], interop);
            while (it.hasNext()) {
                items.add(it.next().getRight());
            }
            Comparator<Object> cmp;
            if (arguments.length == 1) {    // use numerical comparison
                cmp = Interop::compareNumeric;
            } else {
                TruffleObject callback = HeapLanguage.resolveCallbackArgument(arguments[1], interop, "lhs", "rhs");
                cmp = (o1, o2) -> {
                    try {
                        return (int) Interop.asIntegralNumber(interop.execute(callback, o1, o2));
                    } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
                        throw new IllegalArgumentException("Cannot compare values "+o1+" and "+o2, e);
                    }
                };
            }

            items.sort(cmp);
            return Interop.wrapArray(items.toArray());
        }

    }

    /**
     * This function returns the sum of all the elements of the given input array or enumeration. Optionally,
     * accepts an expression as second param. This is used to map the input elements before summing those.
     */
    @ExportLibrary(InteropLibrary.class)
    class Sum implements TruffleObject {

        public static final Sum INSTANCE = new Sum();

        private Sum() {}

        @ExportMessage
        static boolean isExecutable(@SuppressWarnings("unused") Sum receiver) {
            return true;
        }

        @ExportMessage
        static Object execute(@SuppressWarnings("unused") Sum receiver, Object[] arguments)
                throws ArityException, UnsupportedTypeException, UnsupportedMessageException
        {
            Interop.checkArityOptional(arguments, 1, 2);
            InteropLibrary interop = InteropLibrary.getFactory().getUncached();
            long longSum = 0; boolean longValid = true;
            double doubleSum = 0.0; // we keep two sums, because if long works, it will be more precise
            Iterator<Pair<?, ?>> it = Interop.intoIndexedIterator(arguments[0], interop);
            TruffleObject callback = arguments.length == 1 ? null : HeapLanguage.resolveCallbackArgument(arguments[1], interop, "it", "index", "array");
            while (it.hasNext()) {
                Object number;
                if (callback == null) {
                    number = it.next().getRight();
                } else {
                    Pair<?, ?> element = it.next();
                    number = interop.execute(callback, element.getRight(), element.getLeft(), arguments[0]);
                }
                if (longValid) {
                    Long lValue = Interop.tryAsIntegralNumber(number);
                    if (lValue != null) {
                        longSum += lValue;
                        doubleSum += lValue;
                        continue;
                    }
                } // if conversion to long failed, continue here
                longValid = false;
                Double dValue = Interop.tryAsFloatingPointNumber(number);
                if (dValue != null) {
                    doubleSum += dValue;
                } else {
                    throw new IllegalArgumentException("Cannot convert "+number+" to number.");
                }
            }

            return longValid ? longSum : doubleSum;
        }

    }

    /**
     * This function returns an array that contains elements of the input array/enumeration.
     */
    @ExportLibrary(InteropLibrary.class)
    class ToArray implements TruffleObject {

        public static final ToArray INSTANCE = new ToArray();

        private ToArray() {}

        @ExportMessage
        static boolean isExecutable(@SuppressWarnings("unused") ToArray receiver) {
            return true;
        }

        @ExportMessage
        static Object execute(@SuppressWarnings("unused") ToArray receiver, Object[] arguments) throws ArityException {
            Interop.checkArity(arguments, 1);
            InteropLibrary interop = InteropLibrary.getFactory().getUncached();
            Object item = arguments[0];
            if (interop.hasArrayElements(item)) {
                return item;    // if already, array, just return
            } else {
                // TODO: indexed iterator is wasteful here and in unique (maybe also other places?)... simplify...
                Iterator<Pair<?, ?>> it = Interop.intoIndexedIterator(receiver, interop);
                return Interop.wrapIterator(new Iterator<Object>() {
                    @Override
                    public boolean hasNext() {
                        return it.hasNext();
                    }

                    @Override
                    public Object next() {
                        return it.next().getRight();
                    }
                });
            }
        }

    }

    /**
     * This function returns an array/enumeration containing unique elements of the given input array/enumeration.
     */
    @ExportLibrary(InteropLibrary.class)
    class Unique implements TruffleObject {

        public static final Unique INSTANCE = new Unique();

        private Unique() {}

        @ExportMessage
        static boolean isExecutable(@SuppressWarnings("unused") Unique receiver) {
            return true;
        }

        @ExportMessage
        static Object execute(@SuppressWarnings("unused") Unique receiver, Object[] arguments) throws ArityException {
            Interop.checkArity(arguments, 1);
            InteropLibrary interop = InteropLibrary.getFactory().getUncached();
            return Interop.wrapArray(unique(Interop.intoIndexedIterator(arguments[0], interop)));
        }

        @CompilerDirectives.TruffleBoundary
        private static Object[] unique(Iterator<Pair<?, ?>> it) {
            LinkedHashSet<Object> set = new LinkedHashSet<>();
            while (it.hasNext()) {
                set.add(it.next().getRight());
            }
            return set.toArray();
        }

    }

}