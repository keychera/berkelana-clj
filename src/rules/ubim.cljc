(ns rules.ubim
  (:require
   #?(:clj [engine.macros :refer [insert!]]
      :cljs [engine.macros :refer-macros [insert!]])
   [assets.assets :as asset]
   [assets.spritesheet :as spritesheet]
   [clojure.spec.alpha :as s]
   [odoyle.rules :as o]
   [play-cljc.gl.core :as c]
   [play-cljc.transforms :as t]
   [rules.input :as input]
   [rules.pos2d :as pos2d]
   [rules.time :as time]))

(s/def ::anim-tick number?)
(s/def ::anim-elapsed-ms number?)

(s/def ::sprite-from-asset any?)
(s/def ::frame-index int?)

(def rules
  (o/ruleset
   {::move-animation
    [:what
     [::time/now ::time/delta delta-time]
     [keyname ::input/pressed-key keystate]
     [:ubim ::anim-tick anim-tick {:then false}]
     [esse-id ::anim-elapsed-ms anim-elapsed-ms {:then false}]
     [esse-id ::frame-index frame-index {:then false}]
     :when
     (#{:left :right :up :down} keyname)
     :then
     (insert! esse-id
              (merge
               (if (> anim-elapsed-ms 100)
                 {::anim-tick (inc anim-tick) ::anim-elapsed-ms 0}
                 {::anim-elapsed-ms (+ anim-elapsed-ms delta-time)})
               (case keystate
                 ::input/keydown
                 (let [pingpong (case (mod anim-tick 4) 0 -1 1 0 2 1 3 0)]
                   {::frame-index (- (case keyname :down 1 :left 13 :right 25 :up 37 1) pingpong)})

                 ::input/keyup
                 {::anim-elapsed-ms 0
                  ::frame-index (case keyname :down 1 :left 13 :right 25 :up 37 1)}

                 {})))]

    ::ubim-esse
    [:what
     [esse-id ::pos2d/x x]
     [esse-id ::pos2d/y y]
     [esse-id ::frame-index frame-index]
     [esse-id ::sprite-from-asset asset-id]
     [asset-id ::asset/loaded? true]]}))


(defn render [game world camera game-width game-height]
  (let [sprite-esses (o/query-all world ::ubim-esse)]
    (doseq [sprite-esse sprite-esses]
      (let [{:keys [x y asset-id frame-index]} sprite-esse
            {::spritesheet/keys [image frame-height frame-width]} (get @asset/db* asset-id)
            frames-per-row (/ (:width image) frame-width)
            frame-x (mod frame-index frames-per-row)
            frame-y (quot frame-index frames-per-row)
            crop-x (* frame-x frame-width)
            crop-y (* frame-y frame-height)]
        (c/render game (-> image
                           (t/crop crop-x crop-y frame-width frame-height)
                           (t/project game-width game-height)
                           (t/invert camera)
                           (t/translate x y)
                           (t/scale frame-width frame-height)))))))