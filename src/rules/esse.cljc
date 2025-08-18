(ns rules.esse
  (:require
   [assets.asset :as asset]
   [clojure.spec.alpha :as s]
   [engine.utils :refer [deep-merge]]
   [odoyle.rules :as o]))

;; esse is used to replace the word entity from entity-component-system
;; because play-cljc used the word entity that I am not yet sure if it can be conflated or not

(s/def ::anim-tick number?)
(s/def ::anim-elapsed-ms number?)

(s/def ::sprite-from-asset any?)
(s/def ::frame-index int?)

(defn esse
  [session esse-id & maps]
  (o/insert session esse-id (apply deep-merge maps)))

(defn asset
  [session asset-id & maps]
  (swap! asset/db* assoc asset-id (apply deep-merge maps))
  (o/insert session asset-id ::asset/loaded? false))