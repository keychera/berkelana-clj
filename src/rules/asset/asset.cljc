(ns rules.asset.asset
  (:require
   [clojure.spec.alpha :as s]
   [clojure.string :as str]
   [engine.utils :as utils]
   [odoyle.rules :as o]))

;; init, only png for now, or forever
(s/def ::to-load (s/and string? #(str/ends-with? % ".png")))
(s/def ::type #{::spritesheet})

;; asset
(s/def ::loaded? boolean?)
(defonce db* (atom {}))

(def rules
  (o/ruleset
   {::to-load
    [:what
     [asset-id ::to-load asset-path]
     [asset-id ::type asset-type]]}))

(defmulti process-asset (fn [_game _world* req _res] (:asset-type req)))

(defmethod process-asset :default
  [_game _world* {:keys [asset-id asset-path asset-type]} _res]
  (println "asset(" asset-id ") has unhandled type (" asset-type ") . from:" asset-path))

(defn load-asset [game world*]
  (doseq [{:keys [asset-id asset-path asset-type] :as to-load-req} (o/query-all @world* ::to-load)]
    (println "loading" asset-type "asset for" asset-id ", from:" asset-path)
    (utils/get-image asset-path (fn [image-res] (process-asset game world* to-load-req image-res)))))