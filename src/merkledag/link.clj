(ns merkledag.link
  (:refer-clojure :exclude [resolve])
  (:require
    [multihash.core :as multihash])
  (:import
    multihash.core.Multihash))


;; ## Link Table

(def ^:dynamic *link-table*
  "Contextual link table used to collect links when defining nodes, and to
  assign links when parsing nodes."
  nil)


(defn resolve
  "Resolves a link against the current `*link-table*`, if any. Returns nil if
  no matching link is found."
  [name]
  (when-not (string? name)
    (throw (IllegalArgumentException.
             (str "Link name must be a string, got: " (pr-str name)))))
  (some #(when (= name (:name %)) %)
        *link-table*))


(defmulti target
  "Multimethod which returns the multihash identifying the given value."
  class)

(defmethod target nil
  [_]
  nil)

(defmethod target Multihash
  [mhash]
  mhash)



;; ## Merkle Link Type

(def ^:dynamic *get-node*
  "Dynamic var which can be bound to a function which fetches a node from some
  contextual graph repository. If available, this is used to resolve links when
  they are `deref`ed."
  nil)


;; Links have three main properties. Note that **only** link-name and target
;; are used for equality and comparison checks!
;;
;; - `:name` is a string giving the link's name from an object link table.
;; - `:target` is the merklehash to which the link points.
;; - `:tsize` is the total number of bytes reachable from the linked block.
;;   This should equal the sum of the target's links' tsizes, plus the size
;;   of the object itself.
;;
;; In the context of a repo, links can be dereferenced to look up their
;; contents from the store.
(deftype MerkleLink
  [_name _target _tsize _meta]

  Object

  (toString
    [this]
    (format "link:%s:%s:%s" _name (multihash/hex _target) (or _tsize "-")))

  (equals
    [this that]
    (cond
      (identical? this that) true
      (instance? MerkleLink that)
        (and (= _name   (._name   ^MerkleLink that))
             (= _target (._target ^MerkleLink that)))
      :else false))

  (hashCode
    [this]
    (hash-combine (hash _name) (hash _target)))


  Comparable

  (compareTo
    [this that]
    (if (= this that)
      0
      (compare [_name _target]
               [(:name that) (:target that)])))


  clojure.lang.IObj

  (meta [_] _meta)

  (withMeta
    [_ meta-map]
    (MerkleLink. _name _target _tsize meta-map))


  clojure.lang.ILookup

  (valAt
    [this k not-found]
    (case k
      :name _name
      :target _target
      :tsize _tsize
      not-found))

  (valAt
    [this k]
    (.valAt this k nil))


  clojure.lang.IDeref

  (deref
    [this]
    (when-not _target
      (throw (IllegalArgumentException.
               (str "Broken link to " (pr-str _name)
                    " cannot be dereferenced."))))
    (when-not *get-node*
      (throw (IllegalStateException.
               (str "Cannot dereference " this " when no *get-node* function is bound."))))
    (*get-node* _target))


  clojure.lang.IPending

  (isRealized
    [this]
    false))


;; Remove automatic constructor function.
(ns-unmap *ns* '->MerkleLink)


(defn ->link
  "Constructs a `MerkleLink` value, validating the inputs."
  [name target tsize]
  (when-not (string? name)
    (throw (IllegalArgumentException.
             (str "Link name must be a string, got: "
                  (pr-str name)))))
  (when (and target (not (instance? Multihash target)))
    (throw (IllegalArgumentException.
             (str "Link target must be a multihash, got: "
                  (pr-str target)))))
  (when (and tsize (not (integer? tsize)))
    (throw (IllegalArgumentException.
             (str "Link size must be an integer, got: "
                  (pr-str tsize)))))
  (MerkleLink. name target tsize nil))


(defn read-link
  "Resolves a link against the current `*link-table*`, or returns a new broken
  link with a nil target."
  [name]
  (or (resolve name)
      (MerkleLink. name nil nil nil)))