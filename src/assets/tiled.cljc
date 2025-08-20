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
   [assets.assets :as asset]
   [odoyle.rules :as o]))

(def world-map-tmx
  (edn/read-string (read-tiled-map-on-compile "tiled143/world.tmx")))

(s/def ::tilesets-loaded? boolean?)
(s/def ::for keyword?)
(s/def ::game-state map?)

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
             (assoc tileset :images images :tile-size tilewidth)))) ;; assuming square tiles

        {:keys [layers objectgroup map-width map-height firstgids]} asset-data

        tiled-map
        (reduce-kv
         (fn [tiled-map' layer-name layer]
           (reduce
            (fn [tiled-map'' i]
              (let [x        (mod i map-width)
                    y        (int (/ i map-width))
                    gid      (nth layer i)
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
                                ::props   (get props localid)})]
                (cond-> tiled-map''
                  true
                  (assoc-in [:layers layer-name x y] tile)
                  tile
                  (update :tiles conj tile)
                  tile
                  (update :entities conj (t/translate (nth (:images tileset) localid) x y)))))
            tiled-map'
            (range (count layer))))
         {:layers {} :tiles [] :entities []}
         layers)

        tiled-map
        (reduce-kv
         (fn [tiled-map' _id {::keys [layer-name objects]}]
           (reduce
            (fn [tiled-map'' object]
              (let [tile-h   (:height object)
                    _        (assert (= tile-h (:width object))
                                     "assuming square tiles")
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
                                       :localid  localid}
                                      object))]
                (cond-> tiled-map''
                  true
                  (assoc-in [:layers layer-name x y] tile)
                  tile
                  (update :tiles conj tile)
                  tile
                  (update :entities conj (t/translate (nth (:images tileset) localid) x y)))))
            tiled-map'
            objects))
         tiled-map
         objectgroup)

        firstgid->compiled-entity
        (update-vals
         firstgid->tileset
         #(-> %
              (assoc :i 0)
              (update :entity (fn [entity]
                                (c/compile game (instances/->instanced-entity entity))))))

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
                   (sp/transformed [sp/ALL] (fn [{prop :attrs}] [(keyword (:name prop)) (parse-value-type prop)]))
                   (fn [e] (into {} e)))]))
               (fn [[attrs props]] (conj attrs props)))]
             objects-content))

(defmethod asset/process-asset ::asset/tiledmap
  [game world* asset-id {::keys [parsed-tmx]}]
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
                                   (merge firstgid
                                          (update tileset :name #(str asset-id "." %))
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
    (swap! world*
           #(reduce (fn [w t]
                      (-> w
                          (o/insert (:name t) ::tilesets-loaded? false)
                          (o/insert (:name t) ::game-state game)
                          (o/insert (:name t) ::for asset-id))) % tilesets))
    (swap! asset/db*
           (fn [db] (assoc db asset-id (assoc (vars->map layers objectgroup map-width map-height)
                                              :firstgids (->> tilesets (mapv :firstgid) (sort >))))))

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

(defn render-tiled-map [game camera game-width game-height]
  (let [{:keys [::firstgid->entity]} (get @asset/db* :asset/worldmap)
        tile-size (-> firstgid->entity (get 1) :tile-size)]
    (doseq [[_ entity] (->> firstgid->entity (sort-by (fn [[gid _v]] gid)))]
      (c/render game (-> (:entity entity)
                         (t/project game-width game-height)
                         (t/invert camera)
                         (t/translate 8 8)
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

  (= (-> @asset/db* :asset/worldmap ::tiled-map :layers (get "Objects") (get 1) (get 3) :unwalkable)
     (sp/select-one [:asset/worldmap ::tiled-map :layers "Objects" 1 3 :unwalkable] @asset/db*)
     true)

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
        {:gid 454, :height 16, :id 29, :name "Bucket", ::props {:money -5, :unwalkable true}, :width 16, :x 48, :y 96}]))

  (sp/select [:asset/worldmap ::tiled-map :layers (sp/multi-path "Structures" "Interactables") 2 5 some?] @asset/db*))