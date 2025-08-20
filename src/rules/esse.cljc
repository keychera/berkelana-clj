(ns rules.esse
  (:require
   [assets.assets :as asset]
   [engine.utils :refer [deep-merge]]
   [odoyle.rules :as o]))

;; esse is used to replace the word entity from entity-component-system
;; because play-cljc used the word entity that I am not yet sure if it can be conflated or not

(defn esse
  [session esse-id & maps]
  (o/insert session esse-id (apply deep-merge maps)))

(defn asset
  [session asset-id & maps]
  (swap! asset/db* assoc asset-id (apply deep-merge maps))
  (o/insert session asset-id ::asset/loaded? false))