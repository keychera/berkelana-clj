(ns assets.tiled
  (:require
   [engine.macros :refer [read-tiled-map-on-compile s-> vars->map]]
   [assets.assets :as asset]
   [clojure.edn :as edn]
   [clojure.spec.alpha :as s]
   [com.rpl.specter :as sp]
   [engine.utils :as utils]
   [engine.world :as world]
   [odoyle.rules :as o]
   [play-cljc.gl.core :as c]
   [play-cljc.gl.entities-2d :as e2d]
   [play-cljc.instances :as instances]
   [play-cljc.transforms :as t]
   [rules.instanceable :as instanceable]))

(def world-map-tmx
  (edn/read-string (read-tiled-map-on-compile "tiled143/world.tmx")))

(declare load-tile-instances)

(s/def ::tilesets-loaded? boolean?)
(s/def ::for keyword?)

(s/def ::objects-loaded? boolean?)

(def rules
  (o/ruleset
   {::tilesets-to-load
    [:what
     [tileset-name ::tilesets-loaded? loaded?]
     [tileset-name ::for asset-id]
     [::world/global ::world/game game]
     [::asset/global ::asset/db* db*]
     :then
     (let [to-load     (o/query-all session ::tilesets-to-load)
           all-loaded? (reduce #(and (:loaded? %1) (:loaded? %2)) to-load)]
       (when all-loaded?
         (s-> session
              (o/retract tileset-name ::tilesets-loaded?)
              (o/retract tileset-name ::for)
              (o/insert asset-id ::asset/loaded? true))
         (load-tile-instances game db* asset-id)))]

    ::objects-loaded?
    [:what
     [::global ::objects-loaded? loaded?]]}))

;; gids is sorted descendingly
(defn find-firstgid [id firstgids]
  (reduce
   (fn [id v]
     (if (< id v) id (reduced v)))
   id firstgids))

(defn load-tile-instances [game db* asset-id]
  (let [asset-data (get @db* asset-id)
        firstgid->tileset (:firstgid->tileset asset-data)

        firstgid->tileset (update-vals
                           firstgid->tileset
                           (fn [{:keys [tileheight tilewidth entity] :as tileset}]
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
                               (assoc tileset
                                      :images images
                                      :tile-size tilewidth
                                      :width (:width image))))) ;; assuming square tiles

        {:keys [layers objectgroup map-width map-height firstgids]} asset-data

        tiled-map (reduce-kv
                   (fn [tiled-map' layer-name all-gids]
                     (reduce
                      (fn [tiled-map'' i]
                        (let [x        (mod i map-width)
                              y        (int (/ i map-width))
                              gid      (nth all-gids i)
                              firstgid (find-firstgid gid firstgids)
                              localid  (- gid firstgid)
                              tileset  (get firstgid->tileset firstgid)
                              props    (:tile-props tileset)
                              tile     (when (>= gid 1)
                                         {:layer    layer-name
                                          :tile-x   x
                                          :tile-y   y
                                          :firstgid firstgid
                                          :localid  localid
                                          :tile-img (t/translate (nth (:images tileset) localid) x y)
                                          ::props   (get props localid)})]
                          (cond-> tiled-map''
                            :always      (assoc-in [:layers layer-name x y] tile)
                            (some? tile) (update :tiles conj tile))))
                      tiled-map'
                      (range (count all-gids))))
                   {:layers {} :tiles []}
                   layers)

        tiled-map (reduce-kv
                   (fn [tiled-map' _id {::keys [layer-name objects]}]
                     (reduce
                      (fn [tiled-map'' object]
                        (let [tile-h   (:height object)
                              _        (assert (= tile-h (:width object)) "assuming square tiles")
                              x        (/ (:x object) tile-h)
                              y        (dec (/ (:y object) tile-h)) ;; not sure why this needs decrement
                              gid      (:gid object)
                              firstgid (find-firstgid gid firstgids)
                              localid  (- gid firstgid)
                              tileset  (get firstgid->tileset firstgid)
                              tile     (when (>= gid 1)
                                         (merge {:layer    layer-name
                                                 :tile-x   x
                                                 :tile-y   y
                                                 :firstgid firstgid
                                                 :tile-img (t/translate (nth (:images tileset) localid) x y)
                                                 :localid  localid}
                                                object))]
                          (cond-> tiled-map''
                            :always      (assoc-in [:layers layer-name x y] tile)
                            (some? tile) (update :tiles conj tile))))
                      tiled-map'
                      objects))
                   tiled-map
                   objectgroup)

        firstgid->compiled-entity (update-vals
                                   firstgid->tileset
                                   (fn [tileset]
                                     (-> tileset
                                         (assoc :i 0)
                                         (update :entity
                                                 (fn [entity]
                                                   (let [instanced (c/compile game (instances/->instanced-entity entity))
                                                         keyname   (keyword (:name tileset))]
                                                     (swap! db* assoc-in [asset-id keyname ::instanceable/raw] entity)
                                                     (swap! db* assoc-in [asset-id keyname ::instanceable/instanced] instanced)
                                                     instanced))))))

        firstgid->instanced-entity (reduce
                                    (fn [acc tile]
                                      (let [i (get-in acc [(:firstgid tile) :i])]
                                        (if (< (:tile-x tile) 8)
                                          (-> acc
                                              (update-in [(:firstgid tile) :entity] #(instances/assoc % i (:tile-img tile)))
                                              (update-in [(:firstgid tile) :i] inc))
                                          acc)))
                                    firstgid->compiled-entity
                                    (:tiles tiled-map))]
    (swap! db*
           #(-> %
                (update asset-id merge
                        {::tiled-map (-> tiled-map
                                         (merge (vars->map map-width map-height)))
                         ::firstgid->entity firstgid->instanced-entity})))))

(defn parse-value-type [{:keys [value type]}]
  (case type
    "bool" (= value "true")
    "int"  (#?(:clj Integer/parseInt :cljs js/parseInt) value)
    value))

(defn handle-tile-props [tile-props]
  (when (some? tile-props)
    [(sp/select-one [:attrs :id] tile-props)
     (let [props (sp/select [:content sp/ALL :content sp/ALL :attrs] tile-props)]
       (into {}
             (for [prop props]
               [(keyword (:name prop)) (parse-value-type prop)])))]))

;; we just learned about sp/transformed, maybe let's refactor everything with this later on
(defn parse-objects-content [objects-content]
  (sp/select [sp/ALL
              (sp/transformed
               (sp/collect
                (sp/multi-path
                 :attrs
                 [:content sp/ALL #(= (:tag %) :properties) :content (sp/putval ::props)
                  (sp/transformed
                   ;; in clj, fn what is supplied the wrong arity will throw, whil cljs not
                   (sp/transformed sp/ALL (fn [{prop :attrs} & _] [(keyword (:name prop)) (parse-value-type prop)]))
                   (fn [e] (into {} e)))]))
               (fn [[attrs props] & _] (conj attrs props)))]
             objects-content))

(defmethod asset/process-asset ::asset/tiledmap
  [game asset-id {::keys [parsed-tmx]}]
  (let [home-path      (:home-path parsed-tmx)
        map-width      (-> parsed-tmx :attrs :width)
        map-height     (-> parsed-tmx :attrs :height)
        filter-tileset (sp/path [:content (sp/filterer #(= :tileset (:tag %)))])
        filter-image   (sp/path [:content (sp/filterer #(= :image (:tag %)))])
        filter-tile    (sp/path [:content (sp/filterer #(= :tile (:tag %)))])
        tilesets
        (->> (sp/select [filter-tileset sp/ALL
                         (sp/collect (sp/multi-path
                                      [:attrs (sp/view #(select-keys % [:firstgid]))]
                                      [:content sp/FIRST :attrs]
                                      [:content sp/FIRST filter-image sp/ALL :attrs]
                                      [:content sp/ALL filter-tile sp/ALL]))
                         sp/NONE] parsed-tmx)
             (into [] (comp (map first)
                            (map (fn [[firstgid tileset img & tile-props]]
                                   (merge firstgid tileset
                                          {:image (update img :source #(str home-path "/" %))
                                           :tile-props (into {} (map handle-tile-props) tile-props)}))))))
        layers (into {}
                     (comp (filter #(= :layer (:tag %)))
                           (map #(vector
                                  (-> % :attrs :name)
                                  (-> % :content first :content first))))
                     (:content parsed-tmx))
        objectgroup
        (->> (sp/select [:content sp/ALL #(= (:tag %) :objectgroup)
                         (sp/transformed sp/STAY
                                         (fn [{{:keys [id name]} :attrs content :content}]
                                           [id {::layer-name name ::objects (parse-objects-content content)}]))]
                        parsed-tmx)
             (into {}))]
    (swap! (::world/atom* game)
           #(reduce (fn [w t]
                      (-> w
                          (o/insert (:name t) ::tilesets-loaded? false)
                          (o/insert (:name t) ::for asset-id))) % tilesets))
    (swap! (::asset/db* game)
           (fn [db] (update db asset-id merge
                            (assoc (vars->map layers objectgroup map-width map-height)
                                   :firstgids (->> tilesets (mapv :firstgid) (sort >))))))

    (doseq [tileset tilesets]
      (utils/get-image
       (-> tileset :image :source)
       (fn [{:keys [data width height]}]
         (let [image-entity (e2d/->image-entity game data width height)
               loaded-image (assoc image-entity :width width :height height)
               tileset      (assoc tileset :entity loaded-image)]
           (println "loaded tileset asset from" (-> tileset :image :source))
           (swap! (::asset/db* game) #(-> %  (assoc-in [asset-id :firstgid->tileset (:firstgid tileset)] tileset)))
           (swap! (::world/atom* game) #(-> %
                                            (o/insert (:name tileset) ::tilesets-loaded? true)
                                            (o/fire-rules)))))))))

(defn render-tiled-map [game camera game-width game-height]
  (let [{:keys [::firstgid->entity]} (get @(::asset/db* game) :id/worldmap)
        tile-size (-> firstgid->entity (get 1) :tile-size)]
    (doseq [[_ entity] (->> firstgid->entity (sort-by (fn [[gid _v]] gid)))]
      (c/render game (-> (:entity entity)
                         (t/project game-width game-height)
                         (t/invert camera)
                         (t/scale tile-size tile-size))))))
;; this is SCROT from below

(comment
  (= (handle-tile-props
      {:attrs {:id 48},
       :content [{:attrs {},
                  :content
                  [{:attrs {:name "unwalkable", :type "bool", :value "true"}, :content (), :tag :property}],
                  :tag :properties}],
       :tag :tile})
     [48 {:unwalkable true}])

  (let [objects [{:attrs {:gid 454, :height 16, :id 28, :name "Bucket", :width 16, :x 48, :y 96},
                  :content [{:attrs {},
                             :content [{:attrs {:name "money", :type "int", :value 5}, :content (), :tag :property}
                                       {:attrs {:name "unwalkable", :type "bool", :value "false"}, :content (), :tag :property}],
                             :tag :properties}],
                  :tag :object}
                 {:attrs {:gid 454, :height 16, :id 29, :name "Bucket", :width 16, :x 48, :y 96},
                  :content [{:attrs {},
                             :content [{:attrs {:name "money", :type "int", :value -5}, :content (), :tag :property}
                                       {:attrs {:name "unwalkable", :type "bool", :value "true"}, :content (), :tag :property}],
                             :tag :properties}],
                  :tag :object}]]
    (= (parse-objects-content objects)
       [{:gid 454, :height 16, :id 28, :name "Bucket", ::props {:money 5, :unwalkable false}, :width 16, :x 48, :y 96}
        {:gid 454, :height 16, :id 29, :name "Bucket", ::props {:money -5, :unwalkable true}, :width 16, :x 48, :y 96}])))