(ns rules.asset.tiled
  (:require
   #?(:clj [engine.macros :refer [read-tiled-map-on-compile]]
      :cljs [engine.macros :refer-macros [read-tiled-map-on-compile]])
   [clojure.edn :as edn]
   [engine.utils :as utils]
   [play-cljc.gl.core :as c]
   [play-cljc.gl.entities-2d :as e]
   [play-cljc.instances :as instances]
   [play-cljc.transforms :as t]
   [clojure.spec.alpha :as s]))

(defonce world-map
  (edn/read-string (read-tiled-map-on-compile "berkelana-tiled/world.tmx")))

;; from https://github.com/oakes/play-cljc-examples super-koalio, the world we made above have tsx files that need parsing effort
(def tiled-map-koalio
  (edn/read-string (read-tiled-map-on-compile "berkelana-tiled/level1.tmx")))

(comment
  (-> tiled-map-koalio))

;; we initially want to put it inside rules engine but it turns out the query becomes really slow
;; maybe image assets that doesn't need rules engine for storage
(s/def ::tiled-map some?)
(s/def ::entity some?)

(def map* (atom {}))

(defn load-tiled-map [game parsed]
  (let [callback (fn [tiled-map entity] 
                   (swap! map* #(-> % (assoc ::tiled-map tiled-map ::entity entity)))) ;; inline callback for now because we still half understand
        map-home (:home-path parsed)
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
    (println "loading tiled-map from" (:path parsed))
    (utils/get-image (str map-home "/" (-> image :attrs :source))
                     (fn [{:keys [data width height]}]
                       (let [entity (e/->image-entity game data width height)
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
                              {:layers {}
                               :tiles []
                               :entities []}
                              ["background" "walls"])
                             entity (instances/->instanced-entity entity)
                             entity (c/compile game entity)
                             entity (reduce-kv instances/assoc entity entities)]
                         (callback
                          {:layers layers
                           :tiles tiles
                           :map-width map-width
                           :map-height map-height}
                          entity))))))


(defn render-tiled-map [game game-width game-height]
  (let [{::keys [entity tiled-map]} @map*
        {:keys [map-height]} tiled-map
        scaled-tile-size (/ game-height map-height)]
    ;; render the tiled map
    (when entity
      (c/render game (-> entity
                         (t/project game-width game-height)
                         (t/scale scaled-tile-size scaled-tile-size))))))