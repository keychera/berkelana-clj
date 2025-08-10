(ns assets.tiled
  (:require
   #?(:clj [engine.macros :refer [vars->map insert! read-tiled-map-on-compile]]
      :cljs [engine.macros :refer-macros [vars->map insert! read-tiled-map-on-compile]])
   [clojure.edn :as edn]
   [clojure.spec.alpha :as s]
   [com.rpl.specter :as sp]
   [engine.utils :as utils]
   [play-cljc.gl.core :as c]
   [play-cljc.gl.entities-2d :as e2d]
   [play-cljc.instances :as instances]
   [play-cljc.transforms :as t]
   [assets.asset :as asset]
   [odoyle.rules :as o]))

(defonce world-map
  (edn/read-string (read-tiled-map-on-compile "tiled143/world.tmx")))

;; from https://github.com/oakes/play-cljc-examples super-koalio, the world we made above have tsx files that need parsing effort
(defonce tiled-map-koalio
  (edn/read-string (read-tiled-map-on-compile "berkelana-tiled/level1.tmx")))

(s/def ::tiled-map some?)
(s/def ::entity some?)

(def rules
  (o/ruleset
   {::tilesets-to-load
    [:what
     [tileset-name ::tilesets-loaded? loaded?]
     :then
     (let [to-load     (o/query-all session ::tilesets-to-load)
           all-loaded? (reduce #(and %1 %2) to-load)]
       (println "loading progress" to-load)
       (when all-loaded?
         (insert! ::tilesets-to-load ::asset/loaded? match)))]
    
    ::loaded?
    [:what
     [asset-id ::asset/loaded? true]]}))

(def map* (atom {}))

(defn load-tiled-map [game parsed]
  (let [map-home (:home-path parsed)
        map-width (-> parsed :attrs :width)
        map-height (-> parsed :attrs :height)
        tileset (first (filter #(= :tileset (:tag %)) (:content parsed)))
        image (first (filter #(= :image (:tag %)) (:content tileset)))
        {{:keys [tilewidth tileheight]} :attrs} tileset
        layers (->> parsed :content
                    (filter #(= :layer (:tag %)))
                    (map #(vector
                           (-> % :attrs :name)
                           (-> % :content first :content first)))
                    (into {}))]
    (println "loading tiled-map from" (:asset-path parsed))
    (utils/get-image (str map-home "/" (-> image :attrs :source))
                     (fn [{:keys [data width height]}]
                       (let [entity (e2d/->image-entity game data width height)
                             tiles-vert (/ height tileheight)
                             tiles-horiz (/ width tilewidth)
                             images (vec
                                     (for [y (range tiles-vert)
                                           x (range tiles-horiz)]
                                       (t/crop entity
                                               (* x tilewidth)
                                               (* y tileheight)
                                               (- tilewidth 1)
                                               (- tileheight 1))))
                             {:keys [layers tiles entities]}
                             (reduce
                              (fn [m layer-name]
                                (let [layer (get layers layer-name)]
                                  (reduce
                                   (fn [m i]
                                     (let [x (mod i map-width)
                                           y (int (/ i map-width))
                                           image-id (dec (nth layer i))
                                           tile-map (when (>= image-id 0)
                                                      {:layer layer-name :tile-x x :tile-y y})]
                                       (cond-> m
                                         true
                                         (assoc-in [:layers layer-name x y] tile-map)
                                         tile-map
                                         (update :tiles conj tile-map)
                                         tile-map
                                         (update :entities conj
                                                 (t/translate (nth images image-id) x y)))))
                                   m
                                   (range (count layer)))))
                              {:layers {} :tiles [] :entities []}
                              ["background" "walls"])
                             entity (instances/->instanced-entity entity)
                             entity (c/compile game entity)
                             entity (reduce-kv instances/assoc entity entities)]
                         (swap! map* #(-> % (assoc ::tiled-map {:layers layers
                                                                :tiles tiles
                                                                :map-width map-width
                                                                :map-height map-height}
                                                   ::entity entity))))))))

(defmethod asset/process-asset ::asset/tiledmap
  [game world* {:keys [asset-id asset-type parsed-tmx]}]
  (let [asset-path (:asset-path parsed-tmx)
        map-width (-> parsed-tmx :attrs :width)
        map-height (-> parsed-tmx :attrs :height)
        filter-tileset (sp/path [:content (sp/filterer #(= :tileset (:tag %)))])
        filter-image (sp/path [:content (sp/filterer #(= :image (:tag %)))])
        tilesets
        (->> (sp/select [filter-tileset sp/ALL
                         (sp/collect (sp/multi-path
                                      [:attrs (sp/view #(select-keys % [:firstgid]))]
                                      [:content sp/FIRST :attrs]
                                      [:content sp/FIRST filter-image sp/ALL :attrs]))
                         sp/NONE] parsed-tmx)
             (into [] (comp (map first)
                            (map (fn [firstgid tileset img]
                                   {:tileset (merge firstgid (update tileset :name #(str asset-id "." %)))
                                    :image img})))))
        layers (into {}
                     (comp (filter #(= :layer (:tag %)))
                           (map #(vector
                                  (-> % :attrs :name)
                                  (-> % :content first :content first))))
                     (:content parsed-tmx))]

    (swap! world* #(reduce (fn [w t] (o/insert w (:name t) ::tilesets-loaded? false)) % tilesets))
    (swap! asset/db* assoc asset-id (vars->map layers map-width map-height))

    (doseq [tileset tilesets]
      (utils/get-image
       (-> tileset :image :source)
       (fn [{:keys [data width height]}]
         (let [image-entity (e2d/->image-entity game data width height)
               image-entity (c/compile game image-entity)
               loaded-image (assoc image-entity :width width :height height :asset-type asset-type
                                   :tileset tileset)]
           (println "loaded spritesheet asset from" asset-path)
           (swap! asset/db* assoc-in [asset-id (:name tileset)] loaded-image)
           (swap! world* #(-> % (o/insert (:name tileset) ::tilesets-loaded? true)))))))))


(defn render-tiled-map [game game-width game-height]
  (let [{::keys [:assets.tiled/entity :assets.tiled/tiled-map]} @map*
        {:keys [map-height]} tiled-map
        scaled-tile-size (/ game-height map-height)]
    ;; render the tiled map
    (when entity
      (c/render game (-> entity
                         (t/project game-width game-height)
                         (t/scale scaled-tile-size scaled-tile-size))))))