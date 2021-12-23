/*
 * @(#)PersistentTrieSet.java
 * Copyright © 2021 The authors and contributors of JHotDraw. MIT License.
 */
package org.jhotdraw8.collection;


import org.jhotdraw8.annotation.NonNull;
import org.jhotdraw8.annotation.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;


/**
 * Implements the {@link PersistentSet} interface with a
 * Compressed Hash-Array Mapped Prefix-trie (CHAMP).
 * <p>
 * Creating a new copy with a single element added or removed
 * is performed in {@code O(1)} time and space.
 * <p>
 * References:
 * <dl>
 *     <dt>This class has been derived from "The Capsule Hash Trie Collections Library".</dt>
 *     <dd>Copyright (c) Michael Steindorfer, Centrum Wiskunde & Informatica, and Contributors.
 *         BSD 2-Clause License.
 *         <a href="https://github.com/usethesource/capsule">github.com</a>.</dd>
 * </dl>
 *
 * @param <E> the element type
 */
public class PersistentTrieSet<E> extends AbstractReadOnlySet<E> implements PersistentSet<E>, ImmutableSet<E> {

    static final BitmapIndexedNode<?> EMPTY_NODE = new BitmapIndexedNode<>(null, 0, 0, new Object[]{});

    private static final PersistentTrieSet<?> EMPTY_SET = new PersistentTrieSet<>(EMPTY_NODE, 0, 0);

    final BitmapIndexedNode<E> root;
    final int hashCode;
    final int size;

    PersistentTrieSet(BitmapIndexedNode<E> root, int hashCode, int size) {
        this.root = root;
        this.hashCode = hashCode;
        this.size = size;
    }

    @SuppressWarnings("unchecked")
    public static <K> @NonNull PersistentTrieSet<K> copyOf(@NonNull Iterable<? extends K> set) {
        if (set instanceof PersistentTrieSet) {
            return (PersistentTrieSet<K>) set;
        }
        TrieSet<K> tr = new TrieSet<>(of());
        tr.addAll(set);
        return tr.toPersistent();
    }

    @SuppressWarnings("unchecked")
    static <K> @NonNull BitmapIndexedNode<K> emptyNode() {
        return (BitmapIndexedNode<K>) PersistentTrieSet.EMPTY_NODE;
    }

    @SafeVarargs
    public static <K> @NonNull PersistentTrieSet<K> of(@NonNull K... keys) {
        return PersistentTrieSet.<K>of().copyAddAll(Arrays.asList(keys));
    }

    @SuppressWarnings("unchecked")
    public static <K> @NonNull PersistentTrieSet<K> of() {
        return (PersistentTrieSet<K>) PersistentTrieSet.EMPTY_SET;
    }

    /**
     * Returns a copy of this set that is transient.
     * <p>
     * This operation is performed in O(1).
     *
     * @return a transient trie set
     */
    private @NonNull TrieSet<E> toTransient() {
        return new TrieSet<>(this);
    }

    @Override
    public boolean contains(@Nullable final Object o) {
        @SuppressWarnings("unchecked") final E key = (E) o;
        return root.contains(key, Objects.hashCode(key), 0);
    }

    @Override
    public boolean equals(final Object other) {
        if (other == this) {
            return true;
        }
        if (other == null) {
            return false;
        }

        if (other instanceof PersistentTrieSet) {
            PersistentTrieSet<?> that = (PersistentTrieSet<?>) other;
            if (this.size != that.size || this.hashCode != that.hashCode) {
                return false;
            }
            return root.equivalent(that.root);
        } else if (other instanceof ReadOnlySet) {
            @SuppressWarnings("unchecked")
            ReadOnlySet<E> that = (ReadOnlySet<E>) other;
            if (this.size() != that.size()) {
                return false;
            }
            return containsAll(that);
        }

        return false;
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    @Override
    public boolean isEmpty() {
        return size == 0;
    }

    @Override
    public Iterator<E> iterator() {
        return new TrieIterator<>(root);
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public @NonNull PersistentSet<E> copyClear(@NonNull E element) {
        return isEmpty() ? this : of();
    }

    public @NonNull PersistentTrieSet<E> copyAdd(final @NonNull E key) {
        final int keyHash = Objects.hashCode(key);
        final ChangeEvent changeEvent = new ChangeEvent();
        final BitmapIndexedNode<E> newRootNode = (BitmapIndexedNode<E>) root.updated(null, key,
                keyHash, 0, changeEvent);
        if (changeEvent.isModified) {
            return new PersistentTrieSet<>(newRootNode, hashCode + keyHash, size + 1);
        }

        return this;
    }

    @SuppressWarnings("unchecked")
    public @NonNull PersistentTrieSet<E> copyAddAll(final @NonNull Iterable<? extends E> set) {
        if (set == this
                || (set instanceof Collection) && ((Collection<?>) set).isEmpty()
                || (set instanceof ReadOnlyCollection) && ((ReadOnlyCollection<?>) set).isEmpty()) {
            return this;
        }

        if (set instanceof PersistentTrieSet) {
            return copyAddAllFromTrieSet((PersistentTrieSet<E>) set);
        } else if (set instanceof TrieSet) {
            return copyAddAllFromTrieSet(((TrieSet<E>) set).toPersistent());
        }

        final TrieSet<E> t = this.toTransient();
        boolean modified = false;
        for (final E key : set) {
            modified |= t.add(key);
        }
        return modified ? t.toPersistent() : this;
    }

    private @NonNull PersistentTrieSet<E> copyAddAllFromTrieSet(final @NonNull PersistentTrieSet<E> set) {
        if (set.isEmpty()) {
            return this;
        }
        if (this.isEmpty()) {
            return set;
        }
        BulkChangeEvent bulkChange = new BulkChangeEvent();
        bulkChange.hashChange = set.hashCode;
        bulkChange.sizeChange = set.size;
        BitmapIndexedNode<E> newNode = this.root.copyAddAll(set.root, 0, bulkChange);
        if (newNode != this.root) {
            return new PersistentTrieSet<>(newNode,
                    this.hashCode + bulkChange.hashChange,
                    this.size + bulkChange.sizeChange
            );
        }
        return this;
    }

    public @NonNull PersistentTrieSet<E> copyRemove(final @NonNull E key) {
        final int keyHash = Objects.hashCode(key);
        final ChangeEvent changeEvent = new ChangeEvent();
        final BitmapIndexedNode<E> newRootNode = (BitmapIndexedNode<E>) root.removed(null, key,
                keyHash, 0, changeEvent);
        if (changeEvent.isModified) {
            return new PersistentTrieSet<>(newRootNode, hashCode - keyHash, size - 1);
        }

        return this;
    }

    public @NonNull PersistentTrieSet<E> copyRemoveAll(final @NonNull Iterable<? extends E> set) {
        if (this.isEmpty()
                || (set instanceof Collection) && ((Collection<?>) set).isEmpty()
                || (set instanceof ReadOnlyCollection) && ((ReadOnlyCollection<?>) set).isEmpty()) {
            return this;
        }
        if (set == this) {
            return of();
        }
        final TrieSet<E> t = this.toTransient();
        boolean modified = false;
        for (final E key : set) {
            if (t.remove(key)) {
                modified = true;
                if (t.isEmpty()) {
                    break;
                }
            }

        }
        return modified ? t.toPersistent() : this;
    }

    public @NonNull PersistentTrieSet<E> copyRetainAll(final @NonNull Collection<? extends E> set) {
        if (this.isEmpty()) {
            return this;
        }
        if (set.isEmpty()) {
            return of();
        }

        final TrieSet<E> t = this.toTransient();
        boolean modified = false;
        for (E key : this) {
            if (!set.contains(key)) {
                t.remove(key);
                modified = true;
                if (t.isEmpty()) {
                    break;
                }
            }
        }
        return modified ? t.toPersistent() : this;
    }

    static abstract class Node<K> {
        static final int TUPLE_LENGTH = 1;
        static final int HASH_CODE_LENGTH = 32;
        static final int BIT_PARTITION_SIZE = 5;
        static final int BIT_PARTITION_MASK = 0b11111;
        transient final @Nullable PersistentTrieHelper.Nonce bulkEdit;

        Node(@Nullable PersistentTrieHelper.Nonce bulkEdit) {
            this.bulkEdit = bulkEdit;
        }

        static int bitpos(final int mask) {
            return 1 << mask;
        }

        static int index(final int bitmap, final int bitpos) {
            return Integer.bitCount(bitmap & (bitpos - 1));
        }

        static int index(final int bitmap, final int mask, final int bitpos) {
            return (bitmap == -1) ? mask : index(bitmap, bitpos);
        }

        static boolean isAllowedToEdit(PersistentTrieHelper.Nonce x, PersistentTrieHelper.Nonce y) {
            return x != null && (x == y);
        }

        static int mask(final int keyHash, final int shift) {
            return (keyHash >>> shift) & BIT_PARTITION_MASK;
        }


        abstract @NonNull Node<K> copyAddAll(@NonNull Node<K> that, final int shift, BulkChangeEvent bulkChange);

        abstract boolean contains(final K key, final int keyHash, final int shift);

        abstract K getKey(final int index);

        abstract Node<K> getNode(final int index);

        abstract boolean hasNodes();

        abstract boolean hasPayload();

        abstract int nodeArity();

        abstract int payloadArity();

        abstract Node<K> removed(final PersistentTrieHelper.Nonce bulkEdit, final K key, final int keyHash, final int shift,
                                 final ChangeEvent changeEvent);

        abstract PersistentTrieHelper.SizeClass sizePredicate();

        abstract Node<K> updated(final PersistentTrieHelper.Nonce bulkEdit, final K key, final int keyHash, final int shift,
                                 final ChangeEvent changeEvent);

        abstract boolean equivalent(final @NonNull Node<?> other);

    }

    static final class BitmapIndexedNode<K> extends Node<K> {
        private final Object[] nodes;
        private final int nodeMap;
        private final int dataMap;

        BitmapIndexedNode(final @Nullable PersistentTrieHelper.Nonce bulkEdit, final int nodeMap,
                          final int dataMap, final Object[] nodes) {
            super(bulkEdit);
            this.nodeMap = nodeMap;
            this.dataMap = dataMap;
            this.nodes = nodes;
        }

        @Override
        @NonNull BitmapIndexedNode<K> copyAddAll(@NonNull Node<K> o, int shift, BulkChangeEvent bulkChange) {
            // Given the same bit-position in this and that:
            // case                   this.dataMap this.nodeMap that.dataMap  that.nodeMap
            // ---------------------------------------------------------------------------
            //.0    do nothing                -          -            -                -
            //.1    put "a" in dataMap        "a"        -            -                -
            //.2    put x in nodeMap          -          x            -                -
            // 3    illegal                   "a"        x            -                -
            //.4    put "b" in dataMap        -          -            "b"              -
            //.5.1  put "a" in dataMap        "a"        -            "a"              -   values are equal
            //.5.2  put {"a","b"} in nodeMap  "a"        -            "b"              -   values are not equal
            //.6    put x ∪ {"b"} in nodeMap  -          x            "b"              -
            // 7    illegal                   "a"        x            "b"              -
            //.8    put y in nodeMap          -          -            -                y
            //.9    put {"a"} ∪ y in nodeMap  "a"        -            -                y
            //.10.1 put x in nodeMap          -          x            -                x   nodes are equivalent
            //.10.2 put x ∪ y in nodeMap      -          x            -                y   nodes are not equivalent
            // 11   illegal                   "a"        x            -                y
            // 12   illegal                   -          -            "b"              y
            // 13   illegal                   "a"        -            "b"              y
            // 14   illegal                   -          x            "b"              y
            // 15   illegal                   "a"        x            "b"              y

            if (o == this) {
                return this;
            }
            BitmapIndexedNode<K> that = (BitmapIndexedNode<K>) o;

            int newNodeLength = Integer.bitCount(this.nodeMap | this.dataMap | that.nodeMap | that.dataMap);
            Object[] nodesNew = new Object[newNodeLength];
            int nodeMapNew = this.nodeMap | that.nodeMap;
            int dataMapNew = this.dataMap | that.dataMap;
            int thisNodeMapToDo = this.nodeMap;
            int thatNodeMapToDo = that.nodeMap;

            // case 0:
            // we will not have to do any changes
            ChangeEvent changeEvent = new ChangeEvent();
            boolean changed = false;


            // Step 1: Merge that.dataMap and this.dataMap into dataMapNew.
            //         We may have to merge data nodes into sub-nodes.
            // -------
            // iterate over all bit-positions in dataMapNew which have a non-zero bit
            int dataIndex = 0;
            for (int mapToDo = dataMapNew; mapToDo != 0; mapToDo ^= Integer.lowestOneBit(mapToDo)) {
                int mask = Integer.numberOfTrailingZeros(mapToDo);
                int bitpos = bitpos(mask);
                boolean thisHasData = (this.dataMap & bitpos) != 0;
                boolean thatHasData = (that.dataMap & bitpos) != 0;
                if (thisHasData && thatHasData) {
                    K thisKey = this.getKey(index(this.dataMap, bitpos));
                    K thatKey = that.getKey(index(that.dataMap, bitpos));
                    if (Objects.equals(thisKey, thatKey)) {
                        // case 5.1:
                        nodesNew[dataIndex++] = thisKey;
                        bulkChange.hashChange -= Objects.hashCode(thatKey);
                        bulkChange.sizeChange--;
                    } else {
                        // case 5.2:
                        dataMapNew ^= bitpos;
                        nodeMapNew |= bitpos;
                        int thatKeyHash = Objects.hashCode(thatKey);
                        Node<K> subNodeNew = mergeTwoKeyValPairs(bulkEdit, thisKey, Objects.hashCode(thisKey), thatKey, thatKeyHash, shift + BIT_PARTITION_SIZE);
                        nodesNew[nodeIndexAt(nodesNew, nodeMapNew, bitpos)] = subNodeNew;
                        changed = true;
                    }
                } else if (thisHasData) {
                    K thisKey = this.getKey(index(this.dataMap, bitpos));
                    boolean thatHasNode = (that.nodeMap & bitpos) != 0;
                    if (thatHasNode) {
                        // case 9:
                        dataMapNew ^= bitpos;
                        thatNodeMapToDo ^= bitpos;
                        int thisKeyHash = Objects.hashCode(thisKey);
                        changeEvent.isModified = false;
                        Node<K> subNode = that.nodeAt(bitpos);
                        Node<K> subNodeNew = subNode.updated(bulkEdit, thisKey, thisKeyHash, shift + BIT_PARTITION_SIZE, changeEvent);
                        nodesNew[nodeIndexAt(nodesNew, nodeMapNew, bitpos)] = subNodeNew;
                        if (!changeEvent.isModified) {
                            bulkChange.hashChange -= thisKeyHash;
                            bulkChange.sizeChange--;
                        }
                        changed = true;
                    } else {
                        // case 1:
                        nodesNew[dataIndex++] = thisKey;
                    }
                } else {
                    assert thatHasData;
                    K thatKey = that.getKey(index(that.dataMap, bitpos));
                    int thatKeyHash = Objects.hashCode(thatKey);
                    boolean thisHasNode = (this.nodeMap & bitpos) != 0;
                    if (thisHasNode) {
                        // case 6:
                        dataMapNew ^= bitpos;
                        thisNodeMapToDo ^= bitpos;
                        changeEvent.isModified = false;
                        Node<K> subNode = this.getNode(index(this.nodeMap, bitpos));
                        Node<K> subNodeNew = subNode.updated(bulkEdit, thatKey, thatKeyHash, shift + BIT_PARTITION_SIZE, changeEvent);
                        if (changeEvent.isModified) {
                            changed = true;
                            nodesNew[nodeIndexAt(nodesNew, nodeMapNew, bitpos)] = subNodeNew;
                        } else {
                            bulkChange.hashChange -= thatKeyHash;
                            bulkChange.sizeChange--;
                        }
                    } else {
                        // case 4:
                        changed = true;
                        nodesNew[dataIndex++] = thatKey;
                    }
                }
            }

            // Step 2: Merge remaining sub-nodes
            // -------
            int nodeMapToDo = thisNodeMapToDo | thatNodeMapToDo;
            for (int mapToDo = nodeMapToDo; mapToDo != 0; mapToDo ^= Integer.lowestOneBit(mapToDo)) {
                int mask = Integer.numberOfTrailingZeros(mapToDo);
                int bitpos = bitpos(mask);
                boolean thisHasNodeToDo = (thisNodeMapToDo & bitpos) != 0;
                boolean thatHasNodeToDo = (thatNodeMapToDo & bitpos) != 0;
                if (thisHasNodeToDo && thatHasNodeToDo) {
                    //cases 10.1 and 10.2
                    Node<K> thisSubNode = this.getNode(index(this.nodeMap, bitpos));
                    Node<K> thatSubNode = that.getNode(index(that.nodeMap, bitpos));
                    Node<K> subNodeNew = thisSubNode.copyAddAll(thatSubNode, shift + BIT_PARTITION_SIZE, bulkChange);
                    changed |= subNodeNew != thisSubNode;
                    nodesNew[nodeIndexAt(nodesNew, nodeMapNew, bitpos)] = subNodeNew;

                } else if (thatHasNodeToDo) {
                    // case 8
                    Node<K> thatSubNode = that.getNode(index(that.nodeMap, bitpos));
                    nodesNew[nodeIndexAt(nodesNew, nodeMapNew, bitpos)] = thatSubNode;
                    changed = true;
                } else {
                    // case 2
                    assert thisHasNodeToDo;
                    Node<K> thisSubNode = this.getNode(index(this.nodeMap, bitpos));
                    nodesNew[nodeIndexAt(nodesNew, nodeMapNew, bitpos)] = thisSubNode;
                }
            }

            // Step 3: create new node if it has changed
            // ------
            if (changed) {
                return new BitmapIndexedNode<>(bulkEdit, nodeMapNew, dataMapNew, nodesNew);
            }

            return this;
        }


        @Override
        boolean contains(final K key, final int keyHash, final int shift) {
            final int mask = mask(keyHash, shift);
            final int bitpos = bitpos(mask);

            if ((this.dataMap & bitpos) != 0) {
                final int index = index(this.dataMap, mask, bitpos);
                return Objects.equals(getKey(index), key);
            }

            final int nodeMap = nodeMap();
            if ((nodeMap & bitpos) != 0) {
                final int index = index(nodeMap, mask, bitpos);
                return getNode(index).contains(key, keyHash, shift + BIT_PARTITION_SIZE);
            }

            return false;
        }

        private Node<K> copyAndInsertValue(final PersistentTrieHelper.Nonce bulkEdit, final int bitpos,
                                           final K key) {
            final int idx = TUPLE_LENGTH * dataIndex(bitpos);

            // copy 'src' and insert 1 element(s) at position 'idx'
            final Object[] dst = PersistentTrieHelper.copyAdd(this.nodes, idx, key);
            return new BitmapIndexedNode<>(bulkEdit, nodeMap(), dataMap() | bitpos, dst);

        }


        private Node<K> copyAndMigrateFromInlineToNode(final PersistentTrieHelper.Nonce bulkEdit,
                                                       final int bitpos, final Node<K> node) {

            final int idxOld = TUPLE_LENGTH * dataIndex(bitpos);
            final int idxNew = this.nodes.length - TUPLE_LENGTH - nodeIndex(bitpos);
            assert idxOld <= idxNew;

            // copy 'src' and remove 1 element(s) at position 'idxOld' and
            // insert 1 element(s) at position 'idxNew'
            final Object[] src = this.nodes;
            final Object[] dst = new Object[src.length - 1 + 1];
            System.arraycopy(src, 0, dst, 0, idxOld);
            System.arraycopy(src, idxOld + 1, dst, idxOld, idxNew - idxOld);
            System.arraycopy(src, idxNew + 1, dst, idxNew + 1, src.length - idxNew - 1);
            dst[idxNew] = node;

            return new BitmapIndexedNode<>(bulkEdit, nodeMap() | bitpos, dataMap() ^ bitpos, dst);

        }

        private Node<K> copyAndMigrateFromNodeToInline(final PersistentTrieHelper.Nonce bulkEdit,
                                                       final int bitpos, final Node<K> node) {

            final int idxOld = this.nodes.length - 1 - nodeIndex(bitpos);
            final int idxNew = TUPLE_LENGTH * dataIndex(bitpos);
            assert idxOld >= idxNew;

            // copy 'src' and remove 1 element(s) at position 'idxOld' and
            // insert 1 element(s) at position 'idxNew'
            final Object[] src = this.nodes;
            final Object[] dst = new Object[src.length - 1 + 1];
            System.arraycopy(src, 0, dst, 0, idxNew);
            System.arraycopy(src, idxNew, dst, idxNew + 1, idxOld - idxNew);
            System.arraycopy(src, idxOld + 1, dst, idxOld + 1, src.length - idxOld - 1);
            dst[idxNew] = node.getKey(0);

            return new BitmapIndexedNode<>(bulkEdit, nodeMap() ^ bitpos, dataMap() | bitpos, dst);
        }

        private Node<K> copyAndRemoveValue(final PersistentTrieHelper.Nonce bulkEdit, final int bitpos) {
            final int idx = TUPLE_LENGTH * dataIndex(bitpos);

            // copy 'src' and remove 1 element(s) at position 'idx'
            final Object[] dst = PersistentTrieHelper.copyRemove(this.nodes, idx);
            return new BitmapIndexedNode<>(bulkEdit, nodeMap(), dataMap() ^ bitpos, dst);
        }

        private Node<K> copyAndSetNode(final PersistentTrieHelper.Nonce bulkEdit, final int bitpos,
                                       final Node<K> newNode) {

            final int nodeIndex = nodeIndex(bitpos);
            final int idx = this.nodes.length - 1 - nodeIndex;

            if (isAllowedToEdit(this.bulkEdit, bulkEdit)) {
                // no copying if already editable
                this.nodes[idx] = newNode;
                return this;
            } else {
                // copy 'src' and set 1 element(s) at position 'idx'
                final Object[] dst = PersistentTrieHelper.copySet(this.nodes, idx, newNode);
                return new BitmapIndexedNode<>(bulkEdit, nodeMap(), dataMap(), dst);
            }
        }

        private int dataIndex(final int bitpos) {
            return Integer.bitCount(dataMap() & (bitpos - 1));
        }

        private int dataMap() {
            return dataMap;
        }

        @Override
        public boolean equivalent(final @NonNull Node<?> other) {
            if (this == other) {
                return true;
            }
            BitmapIndexedNode<?> that = (BitmapIndexedNode<?>) other;

            // nodes array: we compare local payload from 0 to splitAt (excluded)
            // and then we compare the nested nodes from splitAt to length (excluded)
            int splitAt = payloadArity();
            return nodeMap() == that.nodeMap()
                    && dataMap() == that.dataMap()
                    && Arrays.equals(nodes, 0, splitAt, that.nodes, 0, splitAt)
                    && Arrays.equals(nodes, splitAt, nodes.length, that.nodes, splitAt, that.nodes.length,
                    (a, b) -> ((Node<?>) a).equivalent((Node<?>) b) ? 0 : 1);
        }

        @SuppressWarnings("unchecked")
        @Override
        K getKey(final int index) {
            return (K) nodes[TUPLE_LENGTH * index];
        }

        @SuppressWarnings("unchecked")
        @Override
        Node<K> getNode(final int index) {
            return (Node<K>) nodes[nodes.length - 1 - index];
        }

        @Override
        boolean hasNodes() {
            return nodeMap() != 0;
        }

        @Override
        boolean hasPayload() {
            return dataMap() != 0;
        }

        @Override
        int nodeArity() {
            return Integer.bitCount(nodeMap());
        }

        private Node<K> nodeAt(final int bitpos) {
            return getNode(nodeIndex(bitpos));
        }

        private int nodeIndex(final int bitpos) {
            return Integer.bitCount(nodeMap() & (bitpos - 1));
        }

        private int nodeIndexAt(Object[] array, int nodeMap, final int bitpos) {
            return array.length - 1 - Integer.bitCount(nodeMap & (bitpos - 1));
        }

        private int nodeMap() {
            return nodeMap;
        }

        @Override
        int payloadArity() {
            return Integer.bitCount(dataMap());
        }

        @Override
        Node<K> removed(final PersistentTrieHelper.Nonce bulkEdit, final K key, final int keyHash,
                        final int shift, final ChangeEvent changeEvent) {
            final int mask = mask(keyHash, shift);
            final int bitpos = bitpos(mask);

            if ((dataMap() & bitpos) != 0) { // inplace value
                final int dataIndex = dataIndex(bitpos);

                if (Objects.equals(getKey(dataIndex), key)) {
                    changeEvent.isModified = true;
                    if (this.payloadArity() == 2 && this.nodeArity() == 0) {
                        // Create new node with remaining pair. The new node will a) either become the new root
                        // returned, or b) unwrapped and inlined during returning.
                        final int newDataMap =
                                (shift == 0) ? (dataMap() ^ bitpos) : bitpos(mask(keyHash, 0));

                        if (dataIndex == 0) {
                            return new BitmapIndexedNode<>(bulkEdit, 0, newDataMap, new Object[]{getKey(1)});

                        } else {
                            return new BitmapIndexedNode<>(bulkEdit, 0, newDataMap, new Object[]{getKey(0)});

                        }
                    } else {
                        return copyAndRemoveValue(bulkEdit, bitpos);
                    }
                } else {
                    return this;
                }
            } else if ((nodeMap() & bitpos) != 0) { // node (not value)
                final Node<K> subNode = nodeAt(bitpos);
                final Node<K> subNodeNew =
                        subNode.removed(bulkEdit, key, keyHash, shift + BIT_PARTITION_SIZE, changeEvent);

                if (!changeEvent.isModified) {
                    return this;
                }

                switch (subNodeNew.sizePredicate()) {
                case SIZE_EMPTY:
                    throw new IllegalStateException("Sub-node must have at least one element.");
                case SIZE_ONE:
                    if (this.payloadArity() == 0 && this.nodeArity() == 1) {
                        // escalate (singleton or empty) result
                        return subNodeNew;
                    } else {
                        // inline value (move to front)
                        return copyAndMigrateFromNodeToInline(bulkEdit, bitpos, subNodeNew);
                    }
                default:
                    // modify current node (set replacement node)
                    return copyAndSetNode(bulkEdit, bitpos, subNodeNew);
                }
            }

            return this;
        }

        @Override
        public PersistentTrieHelper.SizeClass sizePredicate() {
            if (this.nodeArity() == 0) {
                switch (this.payloadArity()) {
                case 0:
                    return PersistentTrieHelper.SizeClass.SIZE_EMPTY;
                case 1:
                    return PersistentTrieHelper.SizeClass.SIZE_ONE;
                default:
                    return PersistentTrieHelper.SizeClass.SIZE_MORE_THAN_ONE;
                }
            } else {
                return PersistentTrieHelper.SizeClass.SIZE_MORE_THAN_ONE;
            }
        }

        @Override
        Node<K> updated(final PersistentTrieHelper.Nonce bulkEdit, final K key,
                        final int keyHash, final int shift, final ChangeEvent changeEvent) {
            final int mask = mask(keyHash, shift);
            final int bitpos = bitpos(mask);

            if ((dataMap() & bitpos) != 0) { // inplace value
                final int dataIndex = dataIndex(bitpos);
                final K currentKey = getKey(dataIndex);

                if (Objects.equals(currentKey, key)) {
                    return this;
                } else {
                    final Node<K> subNodeNew = mergeTwoKeyValPairs(bulkEdit, currentKey,
                            currentKey.hashCode(), key, keyHash, shift + BIT_PARTITION_SIZE);

                    changeEvent.isModified = true;
                    return copyAndMigrateFromInlineToNode(bulkEdit, bitpos, subNodeNew);
                }
            } else if ((nodeMap() & bitpos) != 0) { // node (not value)
                final Node<K> subNode = nodeAt(bitpos);
                final Node<K> subNodeNew =
                        subNode.updated(bulkEdit, key, keyHash, shift + BIT_PARTITION_SIZE, changeEvent);

                if (changeEvent.isModified) {
                    // NOTE: subNode and subNodeNew may be referential equal if updated transiently in-place.
                    // Therefore, diffing nodes is not an option. Changes to content and meta-data need to be
                    // explicitly tracked and passed when descending from recursion (i.e., {@code details}).
                    return copyAndSetNode(bulkEdit, bitpos, subNodeNew);
                } else {
                    return this;
                }
            } else {
                // no value
                changeEvent.isModified = true;
                return copyAndInsertValue(bulkEdit, bitpos, key);
            }
        }

        private Node<K> mergeTwoKeyValPairs(PersistentTrieHelper.Nonce bulkEdit,
                                            final K key0, final int keyHash0,
                                            final K key1, final int keyHash1,
                                            final int shift) {
            assert !(key0.equals(key1));

            if (shift >= HASH_CODE_LENGTH) {
                @SuppressWarnings("unchecked")
                HashCollisionNode<K> unchecked = new HashCollisionNode<>(bulkEdit, keyHash0, (K[]) new Object[]{key0, key1});
                return unchecked;
            }

            final int mask0 = mask(keyHash0, shift);
            final int mask1 = mask(keyHash1, shift);

            if (mask0 != mask1) {
                // both nodes fit on same level
                final int dataMap = bitpos(mask0) | bitpos(mask1);
                if (mask0 < mask1) {
                    return new BitmapIndexedNode<>(bulkEdit, 0, dataMap, new Object[]{key0, key1});
                } else {
                    return new BitmapIndexedNode<>(bulkEdit, 0, dataMap, new Object[]{key1, key0});
                }
            } else {
                final Node<K> node = mergeTwoKeyValPairs(bulkEdit, key0, keyHash0, key1, keyHash1, shift + BIT_PARTITION_SIZE);
                // values fit on next level
                final int nodeMap = bitpos(mask0);
                return new BitmapIndexedNode<>(bulkEdit, nodeMap, 0, new Object[]{node});
            }
        }
    }

    private static final class HashCollisionNode<K> extends Node<K> {
        private final int hash;
        private @NonNull K[] keys;

        HashCollisionNode(PersistentTrieHelper.Nonce bulkEdit, final int hash, final K[] keys) {
            super(bulkEdit);
            this.keys = keys;
            this.hash = hash;
            assert payloadArity() >= 2;
        }

        @Override
        @NonNull Node<K> copyAddAll(@NonNull Node<K> o, int shift, BulkChangeEvent bulkChange) {
            if (o == this) {
                return this;
            }
            // The other node must be a HashCollisionNode
            HashCollisionNode<K> that = (HashCollisionNode<K>) o;

            List<K> list = new ArrayList<>(this.keys.length + that.keys.length);

            // Step 1: Add all this.keys to list
            list.addAll(Arrays.asList(this.keys));

            // Step 2: Add all that.keys to list which are not in this.keys
            //         This is quadratic.
            //         If the sets are disjoint, we can do nothing about it.
            //         If the sets intersect, we can mark those which are
            //         equal in a bitset, so that we do not need to check
            //         them over and over again.
            BitSet bs = new BitSet(this.keys.length);
            outer:
            for (int j = 0; j < that.keys.length; j++) {
                K key = that.keys[j];
                for (int i = bs.nextClearBit(0); i >= 0 && i < this.keys.length; i = bs.nextClearBit(i + 1)) {
                    if (Objects.equals(key, this.keys[i])) {
                        bs.set(i);
                        bulkChange.sizeChange--;
                        bulkChange.hashChange -= hash;
                        continue outer;
                    }
                }
                list.add(key);
            }

            if (list.size() > this.keys.length) {
                @SuppressWarnings("unchecked")
                HashCollisionNode<K> unchecked = new HashCollisionNode<>(bulkEdit, hash, (K[]) list.toArray());
                return unchecked;
            }

            return this;
        }

        @Override
        boolean contains(final K key, final int keyHash, final int shift) {
            if (this.hash == keyHash) {
                for (K k : keys) {
                    if (Objects.equals(k, key)) {
                        return true;
                    }
                }
            }
            return false;
        }

        @Override
        public boolean equivalent(final @NonNull Node<?> other) {
            if (this == other) {
                return true;
            }

            HashCollisionNode<?> that = (HashCollisionNode<?>) other;
            if (hash != that.hash
                    || payloadArity() != that.payloadArity()) {
                return false;
            }

            // Linear scan for each key, because of arbitrary element order.
            // ...maybe we could use a bit set to mark keys that we have
            //    found in both sets? But that will cost memory!
            outerLoop:
            for (int i = 0, n = that.payloadArity(); i < n; i++) {
                final Object otherKey = that.getKey(i);

                for (int j = 0, m = keys.length; j < m; j++) {
                    final K key = keys[j];
                    if (Objects.equals(key, otherKey)) {
                        continue outerLoop;
                    }
                }
                return false;
            }
            return true;
        }

        @Override
        K getKey(final int index) {
            return keys[index];
        }

        @Override
        Node<K> getNode(int index) {
            throw new IllegalStateException("Is leaf node.");
        }

        @Override
        boolean hasNodes() {
            return false;
        }

        @Override
        boolean hasPayload() {
            return true;
        }

        @Override
        int nodeArity() {
            return 0;
        }

        @Override
        int payloadArity() {
            return keys.length;
        }

        @Override
        Node<K> removed(final PersistentTrieHelper.Nonce bulkEdit, final K key,
                        final int keyHash, final int shift, final ChangeEvent changeEvent) {
            for (int idx = 0; idx < keys.length; idx++) {
                if (Objects.equals(keys[idx], key)) {
                    changeEvent.isModified = true;

                    if (payloadArity() == 1) {
                        return emptyNode();
                    } else if (payloadArity() == 2) {
                        // Create root node with singleton element.
                        // This node will be a) either be the new root
                        // returned, or b) unwrapped and inlined.
                        final K theOtherKey = (idx == 0) ? keys[1] : keys[0];
                        return new BitmapIndexedNode<>(bulkEdit, 0, bitpos(BitmapIndexedNode.mask(keyHash, 0)), new Object[]{theOtherKey}
                        );
                    } else {
                        // copy 'this.keys' and remove 1 element(s) at position 'idx'
                        final K[] keysNew = PersistentTrieHelper.copyRemove(this.keys, idx);
                        if (isAllowedToEdit(this.bulkEdit, bulkEdit)) {
                            this.keys = keysNew;
                        } else {
                            return new HashCollisionNode<>(bulkEdit, keyHash, keysNew);
                        }
                    }
                }
            }
            return this;
        }

        @Override
        PersistentTrieHelper.SizeClass sizePredicate() {
            return PersistentTrieHelper.SizeClass.SIZE_MORE_THAN_ONE;
        }

        @Override
        public Node<K> updated(final PersistentTrieHelper.Nonce bulkEdit, final K key,
                               final int keyHash, final int shift, final ChangeEvent changeEvent) {
            assert this.hash == keyHash;
            for (K k : keys) {
                if (Objects.equals(k, key)) {
                    return this;
                }
            }
            final K[] keysNew = Arrays.copyOf(keys, keys.length + 1);
            keysNew[keys.length] = key;
            changeEvent.isModified = true;
            if (isAllowedToEdit(this.bulkEdit, bulkEdit)) {
                this.keys = keysNew;
                return this;
            }
            return new HashCollisionNode<>(bulkEdit, keyHash, keysNew);
        }
    }

    /**
     * Iterator skeleton that uses a fixed stack in depth.
     */
    static class TrieIterator<K> implements Iterator<K> {

        private static final int MAX_DEPTH = 7;
        private final int[] nodeCursorsAndLengths = new int[MAX_DEPTH * 2];
        protected int currentValueCursor;
        protected int currentValueLength;
        protected Node<K> currentValueNode;
        @SuppressWarnings({"unchecked", "rawtypes"})
        Node<K>[] nodes = new Node[MAX_DEPTH];
        private int currentStackLevel = -1;

        TrieIterator(Node<K> rootNode) {
            if (rootNode.hasNodes()) {
                currentStackLevel = 0;

                nodes[0] = rootNode;
                nodeCursorsAndLengths[0] = 0;
                nodeCursorsAndLengths[1] = rootNode.nodeArity();
            }

            if (rootNode.hasPayload()) {
                currentValueNode = rootNode;
                currentValueCursor = 0;
                currentValueLength = rootNode.payloadArity();
            }
        }

        public boolean hasNext() {
            if (currentValueCursor < currentValueLength) {
                return true;
            } else {
                return searchNextValueNode();
            }
        }

        @Override
        public K next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            } else {
                return currentValueNode.getKey(currentValueCursor++);
            }
        }

        private boolean searchNextValueNode() {
            while (currentStackLevel >= 0) {
                final int currentCursorIndex = currentStackLevel * 2;
                final int currentLengthIndex = currentCursorIndex + 1;

                final int nodeCursor = nodeCursorsAndLengths[currentCursorIndex];
                final int nodeLength = nodeCursorsAndLengths[currentLengthIndex];

                if (nodeCursor < nodeLength) {
                    final Node<K> nextNode = nodes[currentStackLevel].getNode(nodeCursor);
                    nodeCursorsAndLengths[currentCursorIndex]++;

                    if (nextNode.hasNodes()) {
                        // put node on next stack level for depth-first traversal
                        final int nextStackLevel = ++currentStackLevel;
                        final int nextCursorIndex = nextStackLevel * 2;
                        final int nextLengthIndex = nextCursorIndex + 1;

                        nodes[nextStackLevel] = nextNode;
                        nodeCursorsAndLengths[nextCursorIndex] = 0;
                        nodeCursorsAndLengths[nextLengthIndex] = nextNode.nodeArity();
                    }

                    if (nextNode.hasPayload()) {
                        currentValueNode = nextNode;
                        currentValueCursor = 0;
                        currentValueLength = nextNode.payloadArity();
                        return true;
                    }
                } else {
                    currentStackLevel--;
                }
            }

            return false;
        }
    }


    static class ChangeEvent {
        boolean isModified;
    }

    static class BulkChangeEvent {
        int sizeChange;
        int hashChange;
    }
}