(ns assets.asset
  (:require
   [clojure.spec.alpha :as s]
   [clojure.string :as str]
   [odoyle.rules :as o]))

;; init, only png for now, or forever
(s/def ::img-to-load (s/and string? #(str/ends-with? % ".png")))
(s/def ::type #{::spritesheet ::tiledmap})

;; asset
(s/def ::loaded? boolean?)
(defonce db* (atom {}))

@db*

(def rules
  (o/ruleset
   {::to-load
    [:what
     [asset-id ::loaded? loaded]]}))

(defmulti process-asset (fn [_game _world* _asset-id asset-data] (::type asset-data)))

(defmethod process-asset :default
  [_game _world* asset-id {:keys [asset-type] :as _asset_data}]
  (println "asset(" asset-id ") has unhandled type (" asset-type ")"))

(defn load-asset [game world*]
  (doseq [{:keys [asset-id]} (o/query-all @world* ::to-load)]
    (let [asset-data (get @db* asset-id)]
      (println "loading" (::type asset-data) "asset for" asset-id)
      (process-asset game world* asset-id asset-data))))