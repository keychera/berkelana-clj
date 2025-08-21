(ns assets.texts
  (:require
   #?(:clj [assets.on-compile.fonts :as fonts :refer [load-font-clj]]
      :cljs [assets.on-compile.fonts :as fonts :refer-macros [load-font-cljs]])
   #?(:clj [engine.macros :refer [s->]]
      :cljs [engine.macros :refer-macros [s->]])
   [assets.chars :as chars]
   [odoyle.rules :as o]
   [play-cljc.gl.core :as c]
   [play-cljc.gl.text :as gl-text]
   [play-cljc.instances :as i]
   [play-cljc.transforms :as t]
   [rules.input :as input]
   [rules.ubim :as ubim]
   [clojure.spec.alpha :as s]))

;; from example play-cljc-examples/ui-gallery/src/ui_gallery

;; in clj, (swap! texts* update :counter inc) will throw NPE if map is empty on init
(defonce texts* (atom {:counter 0}))

(s/def ::test-counter int?)

(def rules
  (o/ruleset
   {::test-counter
    [:what
     [keyname ::input/pressed-key ::input/keydown]
     [::test ::test-counter cnt {:then false}]
     :then
     (s-> session 
          (o/insert ::test ::test-counter (inc cnt)))]}))

(defn init [game]
  (#?(:clj load-font-clj :cljs load-font-cljs)
   ::fonts/cardboard-crown
   (fn [{:keys [data]} baked-font]
     (let [font-entity (gl-text/->font-entity game data baked-font)
           dynamic-entity (c/compile game (i/->instanced-entity font-entity))]
       (swap! texts* assoc
              :font-entity font-entity
              :dynamic-entity dynamic-entity)))))

(defn render [game world camera game-width game-height]
  (let [{:keys [x y]} (first (o/query-all world ::ubim/ubim-esse)) 
        test-counter  (first (o/query-all world ::test-counter)) 
        texts (swap! texts* update :counter inc) 
        {:keys [font-entity dynamic-entity counter]} texts] 
    (when (and (pos? game-width) (pos? game-height) font-entity dynamic-entity counter)
      (let [text [(str test-counter) "Frame count: " (str counter)]]
        (c/render game (-> (reduce
                            (partial apply chars/assoc-char)
                            dynamic-entity
                            (for [line-num (range (count text))
                                  char-num (range (count (nth text line-num)))
                                  :let [ch (get-in text [line-num char-num])]]
                              [line-num char-num
                               (-> font-entity
                                   (chars/crop-char ch)
                                   (t/color [1 1 1 1]))]))
                           (t/project game-width game-height)
                           (t/invert camera)
                           (t/translate (+ x 8) (- y 16))
                           (t/scale 0.2 0.2)))))))