(ns assets.texts
  (:require
   #?(:clj [assets.on-compile.fonts :as fonts :refer [load-font-clj]]
      :cljs [assets.on-compile.fonts :as fonts :refer-macros [load-font-cljs]])
   [assets.chars :as chars]
   [clojure.spec.alpha :as s]
   [engine.world :as world]
   [odoyle.rules :as o]
   [play-cljc.gl.core :as c]
   [play-cljc.gl.text :as gl-text]
   [play-cljc.instances :as i]
   [play-cljc.transforms :as t]
   [rules.pos2d :as pos2d]))

;; from example play-cljc-examples/ui-gallery/src/ui_gallery

(s/def ::font-instances* #(instance? #?(:clj clojure.lang.Atom :cljs Atom) %))

(s/def ::lines (s/coll-of string? :kind vector?))
(s/def ::progress int?)
(s/def ::width number?)
(s/def ::content
  (s/keys :req-un [::lines ::pos2d/x ::pos2d/y]
          :opt-un [::progress ::width]))

(world/system system
  {::world/rules
   (o/ruleset
    {::texts-to-render
     [:what
      [text-id ::content content]]})

   ::world/render-fn
   (fn text-render [world game camera game-width game-height]
     (let [{:keys [dynamic-entity font-entity]} @(::font-instances* game)]
       (when (and dynamic-entity font-entity)
         (doseq [text (->> (o/query-all world ::texts-to-render) (map :content))]
           (let [{:keys [lines x y progress]} text]
             (c/render game (-> (chars/instance-assoc-lines dynamic-entity font-entity lines progress)
                                (t/project game-width game-height)
                                (t/invert camera)
                                (t/translate x y)
                                (t/scale 0.2 0.2))))))))})

(defn init [game]
  (#?(:clj load-font-clj :cljs load-font-cljs)
   ::fonts/cardboard-crown
   (fn [{:keys [data]} baked-font]
     (let [font-entity (gl-text/->font-entity game data baked-font)
           dynamic-entity (c/compile game (i/->instanced-entity font-entity))]
       (swap! (::font-instances* game) assoc
              :font-entity font-entity
              :dynamic-entity dynamic-entity)))))