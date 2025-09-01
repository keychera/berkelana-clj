(ns assets.assets
  (:require
   [clojure.spec.alpha :as s]
   [clojure.string :as str]
   [engine.world :as world]
   [odoyle.rules :as o]))

;; init, only png for now, or forever
(s/def ::img-to-load (s/and string? #(str/ends-with? % ".png")))
(s/def ::type #{::spritesheet ::tiledmap})

;; asset
(s/def ::loaded? boolean?)
(s/def ::db* #(instance? #?(:clj clojure.lang.Atom :cljs Atom) %))

(def system
  {::world/init
   (fn [world]
     (o/insert world ::global ::db* (atom {})))

   ::world/rules
   (o/ruleset
    {::db*
     [:what [::global ::db* db*]]

     ::to-load
     [:what [asset-id ::loaded? loaded]]})})

(defmulti process-asset (fn [_game _db* _asset-id asset-data] (::type asset-data)))

(defmethod process-asset :default
  [_game _db* asset-id {:keys [asset-type] :as _asset_data}]
  (println "asset(" asset-id ") has unhandled type (" asset-type ")"))

(defn load-asset [game world]
  (let [db* (:db* (first (o/query-all world ::db*)))]
    (doseq [{:keys [asset-id]} (o/query-all @world/world* ::to-load)]
      (let [asset-data (get @db* asset-id)]
        (println "loading" (::type asset-data) "asset for" asset-id)
        (process-asset game db* asset-id asset-data)))))