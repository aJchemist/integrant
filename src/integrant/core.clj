(ns integrant.core
  (:refer-clojure :exclude [ref read-string])
  (:require [com.stuartsierra.dependency :as dep]
            [clojure.edn :as edn]
            [clojure.walk :as walk]))

(defrecord Ref [key])

(defn ref [key]
  {:pre [(keyword? key)]}
  (->Ref key))

(defn ref? [x]
  (instance? Ref x))

(defn- find-refs [v]
  (cond
    (ref? v)  (list (:key v))
    (coll? v) (mapcat find-refs v)))

(defn dependencies [m]
  (reduce-kv (fn [g k v] (reduce #(dep/depend %1 k %2) g (find-refs v)))
             (dep/graph)
             m))

(defn read-string [s]
  (edn/read-string {:readers {'ref ref}} s))

(defn expand
  ([m]
   (reduce expand m (keys m)))
  ([m k]
   (let [v (m k)]
     (walk/postwalk #(if (and (ref? %) (= k (:key %))) v %) m))))

(defmulti run-key (fn [k v] k))

(defmethod run-key :default [_ v] v)

(defmulti halt-key! (fn [k v] k))

(defmethod halt-key! :default [_ v])

(defn- sort-keys [ks m]
  (sort (dep/topo-comparator (dependencies m)) ks))

(defn- update-key [m k]
  (-> m (update k (partial run-key k)) (expand k)))

(defn run
  ([m]
   (run m (keys m)))
  ([m ks]
   (reduce update-key m (sort-keys ks m))))

(defn halt!
  ([m]
   (halt! m (keys m)))
  ([m ks]
   (doseq [k (reverse (sort-keys ks m))]
     (halt-key! k (m k)))))