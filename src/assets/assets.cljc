(ns assets.assets
  (:require
   [clojure.spec.alpha :as s]
   [clojure.string :as str]
   [engine.world :as world]
   [odoyle.rules :as o]))

;; init, only png for now, or forever
(s/def ::img-to-load (s/and string? #(str/ends-with? % ".png")))
(s/def ::type #{::spritesheet ::tiledmap})

;; assets
(s/def ::loaded? boolean?)
(s/def ::db* #(instance? #?(:clj clojure.lang.Atom :cljs Atom) %))

(world/system system
  {::world/init-fn
   (fn [world game]
     (o/insert world ::global ::db* (::db* game)))

   ::world/rules
   (o/ruleset
    {::db*
     [:what [::global ::db* db*]]

     ::to-load
     [:what [asset-id ::loaded? loaded?]]})})

(defmulti process-asset (fn [_game _asset-id asset-data] (::type asset-data)))

(defmethod process-asset :default
  [_game asset-id {:keys [asset-type] :as _asset_data}]
  (println "asset(" asset-id ") has unhandled type (" asset-type ")"))

(defn load-asset
  "load-asset must be the last after every system init because of the order below:
   1. `assets/system` insert `::assets/db*` to the `::world/global` in `::world/init-fn` that is ordered early in `engine/all-systems`
   2. `rules.esse/asset` will populate `::assets/db*` in other system's `::world/init-fn`
   3. `assets/load-asset` will be load all `[::asset/loaded? false]` last in `engine/compile-all`
   "
  [game] 
  (doseq [{:keys [asset-id loaded?]} (o/query-all @(::world/atom* game) ::to-load)]
    (when (not loaded?)
      (let [asset-data (get @(::db* game) asset-id)]
        (println "loading" (::type asset-data) "asset for" asset-id)
        (process-asset game asset-id asset-data)))))