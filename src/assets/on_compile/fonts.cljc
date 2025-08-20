(ns assets.on-compile.fonts
  #?(:clj (:require [play-cljc.text :as text])))

(def bitmap-size 512)

;; we use cljc so that qualified namespace will be picked up by clojure-lsp on both worlds
;; then calva will have nice drop-down on font selections from this ns

#?(:clj
   (def bitmaps {::cardboard-crown (text/->bitmap bitmap-size bitmap-size)
                 ::royal-fibre (text/->bitmap bitmap-size bitmap-size)}))
#?(:clj
   (def baked-fonts {::cardboard-crown (text/->baked-font "fonts/CardboardCrown.ttf" 32 (::cardboard-crown bitmaps))
                     ::royal-fibre (text/->baked-font "fonts/RoyalFibre.ttf" 16 (::royal-fibre bitmaps))}))

#?(:clj
   (defn load-font-clj [font-key callback]
     (callback (font-key bitmaps) (font-key baked-fonts))))

#?(:clj
   (defmacro load-font-cljs [font-key callback]
     (let [{:keys [width height] :as bitmap} (font-key bitmaps)
           data-uri (text/bitmap->data-uri bitmap)
           baked    (font-key baked-fonts)]
       `(let [image# (js/Image. ~width ~height)]
          (doto image#
            (-> .-src (set! ~data-uri))
            (-> .-onload (set! #(~callback {:data image# :width ~width :height ~height} ~baked))))))))
