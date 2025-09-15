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
   [rules.dev.dev-only :as dev-only]
   [rules.pos2d :as pos2d]))

;; from example play-cljc-examples/ui-gallery/src/ui_gallery

(s/def ::font-instances* #(instance? #?(:clj clojure.lang.Atom :cljs Atom) %))

(s/def ::lines (s/coll-of string? :kind vector?))

(world/system system
  {::world/rules
   (o/ruleset
    {::texts-to-render
     [:what
      [text-id ::lines lines]
      [text-id ::pos2d/pos2d pos]]})

   ::world/render-fn
   (fn text-render [world game camera game-width game-height]
     (let [{:keys [dynamic-entity font-entity]} @(::font-instances* game)]
       (when (and dynamic-entity font-entity)
         (doseq [text (o/query-all world ::texts-to-render)]
           (let [{:keys [lines pos]} text
                 {:keys [x y]} pos]
             (dev-only/inspect-game! game "che2ck" x y)
             (c/render game (-> (chars/instance-assoc-lines dynamic-entity font-entity lines)
                                (t/project game-width game-height)
                                (t/invert camera)
                                (t/translate (+ x 8) (- y 26))
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