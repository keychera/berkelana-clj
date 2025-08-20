(ns assets.texts
  (:require
   #?(:clj [assets.on-compile.fonts :as fonts :refer [load-font-clj]]
      :cljs [assets.on-compile.fonts :as fonts :refer-macros [load-font-cljs]])
   [assets.chars :as chars]
   [play-cljc.gl.core :as c]
   [play-cljc.gl.text :as gl-text]
   [play-cljc.instances :as i]
   [play-cljc.transforms :as t]))

;; from example play-cljc-examples/ui-gallery/src/ui_gallery

(defonce state* (atom {}))

(defn init [game]
  (#?(:clj load-font-clj :cljs load-font-cljs)
   ::fonts/cardboard-crown
   (fn [{:keys [data]} baked-font]
     (let [font-entity (gl-text/->font-entity game data baked-font)
           compiled-font-entity (c/compile game font-entity)
           ;; an entity whose text can't change
           static-entity (c/compile game (gl-text/->text-entity game compiled-font-entity "Hello, world!"))
           ;; an entity whose text can be set dynamically
           dynamic-entity (c/compile game (i/->instanced-entity font-entity))]
       (swap! state* assoc
              :font-entity font-entity
              :static-entity static-entity
              :dynamic-entity dynamic-entity)))))

(defn render [game game-width game-height]
  (let [state (swap! state* update :counter inc)
        {:keys [font-entity
                static-entity
                dynamic-entity
                counter]} state]
    (when (and (pos? game-width) (pos? game-height))
      (when (and static-entity dynamic-entity)
        ;; render the static text
        (c/render game (-> static-entity
                           (t/project game-width game-height)
                           (t/scale (:width static-entity) (:height static-entity))
                           (t/translate 0 0)))
        ;; render the colored text
        (c/render game (-> (reduce-kv
                            chars/assoc-char
                            dynamic-entity
                            (mapv (fn [ch color]
                                    (-> font-entity
                                        (chars/crop-char ch)
                                        (t/color color)))
                                  "Colors"
                                  (cycle
                                   [[1 0 0 1]
                                    [0 1 0 1]
                                    [0 0 1 1]])))
                           (t/project game-width game-height)
                           (t/translate 0 100)))
        ;; render the frame count
        (let [text ["Frame count:" (str counter)]]
          (c/render game (-> (reduce
                              (partial apply chars/assoc-char)
                              dynamic-entity
                              (for [line-num (range (count text))
                                    char-num (range (count (nth text line-num)))
                                    :let [ch (get-in text [line-num char-num])]]
                                [line-num char-num (chars/crop-char font-entity ch)]))
                             (t/project game-width game-height)
                             (t/translate 100 200)
                             (t/scale 2 2))))))))