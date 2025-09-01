(ns rules.esse
  (:require
   [assets.assets :as asset]
   [engine.utils :refer [deep-merge]]
   [odoyle.rules :as o]))

;; esse is used to replace the word entity from entity-component-system
;; because play-cljc used the word entity that I am not yet sure if it can be conflated or not

(defn esse
  [world esse-id & maps]
  (o/insert world esse-id (apply deep-merge maps)))

(defn asset
  [world asset-id & maps]
  (let [db* (:db* (first (o/query-all world ::asset/db*)))]
    (swap! db* assoc asset-id (apply deep-merge maps)))
  (o/insert world asset-id ::asset/loaded? false))