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
(s/def ::to-render
  (s/keys :req-un [::lines ::pos2d/x ::pos2d/y]))

(world/system system
  {::world/rules
   (o/ruleset
    {::texts-to-render
     [:what
      [text-id ::to-render to-render]]})

   ::world/render-fn
   (fn text-render [world game camera game-width game-height]
     (let [{:keys [font-entity dynamic-entity]} @(::font-instances* game)]
       (when (and font-entity dynamic-entity)
         (doseq [text (o/query-all world ::texts-to-render)]
           (let [{:keys [lines x y]} (:to-render text)]
             (dev-only/inspect-game! game "che2ck" x y)
             (c/render game (-> (reduce
                                 (partial apply chars/assoc-char)
                                 dynamic-entity
                                 (for [line-num (range (count lines))
                                       char-num (range (count (nth lines line-num)))
                                       :let [ch (get-in lines [line-num char-num])]]
                                   [line-num char-num
                                    (-> font-entity
                                        (chars/crop-char ch)
                                        (t/color [1 1 1 1]))]))
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