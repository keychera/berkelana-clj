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

(world/system system
  {::world/init-fn
   (fn [game world]
     (o/insert world ::global ::db* (::db* game)))

   ::world/rules
   (o/ruleset
    {::db*
     [:what [::global ::db* db*]]

     ::to-load
     [:what [asset-id ::loaded? loaded]]})})

(defmulti process-asset (fn [_game _asset-id asset-data] (::type asset-data)))

(defmethod process-asset :default
  [_game asset-id {:keys [asset-type] :as _asset_data}]
  (println "asset(" asset-id ") has unhandled type (" asset-type ")"))

(defn load-asset [game]
  (doseq [{:keys [asset-id]} (o/query-all @(::world/atom* game) ::to-load)]
    (let [asset-data (get @(::db* game) asset-id)]
      (println "loading" (::type asset-data) "asset for" asset-id)
      (process-asset game asset-id asset-data))))