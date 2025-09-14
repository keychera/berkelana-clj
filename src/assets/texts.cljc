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
   [rules.dev.leva-rules :as leva-rules]))

;; from example play-cljc-examples/ui-gallery/src/ui_gallery

(s/def ::font-instances* #(instance? #?(:clj clojure.lang.Atom :cljs Atom) %))

(s/def ::counter int?)
(s/def ::texts vector?)

(world/system system
  {::world/init-fn
   (fn [_game world]
     (o/insert world ::test ::counter 0))

   ::world/rules
   (o/ruleset
    {::counter
     [:what
      [::test ::texts texts]
      [::test ::counter cnt]]})

   ::world/render-fn
   (fn text-render [game world camera game-width game-height]

     (doseq [[x y] [[32 128] [24 100] [42 80]]]
       (let [limit #?(:clj 64 :cljs (some-> (first (o/query-all world ::leva-rules/dev-slider)) :value))
             {:keys [texts cnt]} (first (o/query-all world ::counter))
             {:keys [font-entity dynamic-entity]} @(::font-instances* game)]
         (when (and font-entity dynamic-entity texts)
           (let [text (subvec texts 0 (mod cnt (inc (count texts))))]
             (swap! (::world/atom* game) #(dev-only/send-dev-value % (str text)))
             (c/render game (-> (reduce
                                 (partial apply chars/assoc-char)
                                 dynamic-entity
                                 (for [line-num (range (count text))
                                       char-num (range (min limit (count (nth text line-num))))
                                       :let [ch (get-in text [line-num char-num])]]
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