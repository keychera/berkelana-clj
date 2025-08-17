(ns assets.tiled
  (:require
   #?(:clj [engine.macros :refer [vars->map s-> read-tiled-map-on-compile]]
      :cljs [engine.macros :refer-macros [vars->map s-> read-tiled-map-on-compile]])
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

(def world-map-tmx
  (edn/read-string (read-tiled-map-on-compile "tiled143/world.tmx")))

(s/def ::tilesets-loaded? boolean?)
(s/def ::for keyword?)
(s/def ::game-state map?)

(let [{:keys [layers map-width firstgids]}
      (-> (get @asset/db* :asset/worldmap)
          (dissoc 1) (dissoc 17) (dissoc :parsed-tmx))]
  [(keys layers) map-width firstgids])

;; gids is sorted descendingly
(defn find-firstgid [id firstgids]
  (reduce
   (fn [id v]
     (if (< id v) id (reduced v)))
   id firstgids))

(defn load-tile-instances [game asset-id]
  (let [asset-data (get @asset/db* asset-id)
        firstgid->tileset (:firstgid->tileset asset-data)

        firstgid->tileset
        (update-vals
         firstgid->tileset
         (fn [{:keys [tileheight tilewidth entity]
               :as   tileset}]
           (let [image       (:image tileset)
                 tiles-vert  (/ (:height image) tileheight)
                 tiles-horiz (/ (:width image) tilewidth)
                 images      (vec
                              (for [y (range tiles-vert)
                                    x (range tiles-horiz)]
                                (t/crop entity
                                        (* x tilewidth)
                                        (* y tileheight)
                                        (- tilewidth 1)
                                        (- tileheight 1))))]
             (assoc tileset :images images))))

        {:keys [layers map-width map-height firstgids]} asset-data

        tiled-map
        (reduce-kv
         (fn [layers* layer-name layer]
           (reduce
            (fn [m i]
              (let [x        (mod i map-width)
                    y        (int (/ i map-width))
                    gid      (nth layer i)
                    firstgid (find-firstgid gid firstgids)
                    localid  (- gid firstgid)
                    tileset  (get firstgid->tileset firstgid)
                    tile-map (when (>= gid 1) {:layer    layer-name
                                               :tile-x   x
                                               :tile-y   y
                                               :firstgid firstgid
                                               :localid localid})]
                (cond-> m
                  true
                  (assoc-in [:layers layer-name x y] tile-map)
                  tile-map
                  (update :tiles conj tile-map)
                  tile-map
                  (update :entities conj (t/translate (nth (:images tileset) localid) x y)))))
            layers*
            (range (count layer))))
         {:layers   {}
          :tiles    []
          :entities []}
         layers)

        firstgid->compiled-entity
        (update-vals
         firstgid->tileset
         (fn [{:keys [entity]}]
           {:i 0 :entity (c/compile game (instances/->instanced-entity entity))}))

        firstgid->instanced-entity
        (reduce
         (fn [acc [tile uncompiled-entity]]
           (let [i (get-in acc [(:firstgid tile) :i])]
             (-> acc
                 (update-in [(:firstgid tile) :entity] #(instances/assoc % i uncompiled-entity))
                 (update-in [(:firstgid tile) :i] inc))))
         firstgid->compiled-entity
         (map vector (:tiles tiled-map) (:entities tiled-map)))]
    (swap! asset/db*
           #(-> %
                (assoc asset-id {::tiled-map (-> tiled-map
                                                 (dissoc :entities)
                                                 (merge (vars->map map-width map-height)))
                                 ::firstgid->entity firstgid->instanced-entity})))))

(def rules
  (o/ruleset
   {::tilesets-to-load
    [:what
     [tileset-name ::tilesets-loaded? loaded?]
     [tileset-name ::for asset-id]
     [tileset-name ::game-state passed-game] ;; feels hacky, we definitely use rules engine where it's not supposed to be used
     :then
     (let [to-load     (o/query-all session ::tilesets-to-load)
           all-loaded? (reduce #(and (:loaded? %1) (:loaded? %2)) to-load)]
       (when all-loaded?
         (s-> session
              (o/retract tileset-name ::tilesets-loaded?)
              (o/retract tileset-name ::for)
              (o/retract tileset-name ::game-state)
              (o/insert asset-id ::asset/loaded? true))
         (load-tile-instances passed-game asset-id)))]}))

(defmethod asset/process-asset ::asset/tiledmap
  [game world* asset-id {::keys [parsed-tmx]}]
  (let [home-path (:home-path parsed-tmx)
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
                            (map (fn [[firstgid tileset img]]
                                   (merge firstgid
                                          (update tileset :name #(str asset-id "." %))
                                          {:image (update img :source #(str home-path "/" %))}))))))
        layers (into {}
                     (comp (filter #(= :layer (:tag %)))
                           (map #(vector
                                  (-> % :attrs :name)
                                  (-> % :content first :content first))))
                     (:content parsed-tmx))]
    (swap! world*
           #(reduce (fn [w t]
                      (-> w
                          (o/insert (:name t) ::tilesets-loaded? false)
                          (o/insert (:name t) ::game-state game)
                          (o/insert (:name t) ::for asset-id))) % tilesets))
    (swap! asset/db*
           (fn [db] (assoc db asset-id (assoc (vars->map layers map-width map-height)
                                              :firstgids (->>  tilesets (mapv :firstgid) (sort >))))))

    (doseq [tileset tilesets]
      (utils/get-image
       (-> tileset :image :source)
       (fn [{:keys [data width height]}]
         (let [image-entity (e2d/->image-entity game data width height)
               loaded-image (assoc image-entity :width width :height height)
               tileset      (assoc tileset :entity loaded-image)]
           (println "loaded tileset asset from" (-> tileset :image :source))
           (swap! asset/db* #(-> %  (assoc-in [asset-id :firstgid->tileset (:firstgid tileset)] tileset)))
           (swap! world* #(-> %
                              (o/insert (:name tileset) ::tilesets-loaded? true)
                              (o/fire-rules)))))))))

(defn render-tiled-map [game game-width game-height]
  (let [{:keys [::firstgid->entity]} (get @asset/db* :asset/worldmap)
        scaled-tile-size 64]
    ;; render the tiled map
    (doseq [[_ entity] (->> firstgid->entity (sort-by (fn [[gid _v]] gid)))]
      (c/render game (-> (:entity entity)
                         (t/project game-width game-height)
                         (t/translate 32 32)
                         (t/scale scaled-tile-size scaled-tile-size))))))