(ns rules.ubim
  (:require
   [clojure.spec.alpha :as s]
   [engine.macros :refer [insert!]]
   [odoyle.rules :as o]
   [rules.input :as input]
   [rules.sprites :as sprites]
   [rules.time :as time]))

(s/def ::anim-tick number?)
(s/def ::anim-elapsed-ms number?)

(def rules
  (o/ruleset
   {::ubim-animation
    [:what
     [::time/now ::time/delta delta-time]
     [keyname ::input/pressed-key keystate]
     [:chara/ubim ::anim-tick anim-tick {:then false}]
     [:chara/ubim ::anim-elapsed-ms anim-elapsed-ms {:then false}]
     :when
     (#{::input/left ::input/right ::input/up ::input/down} keyname)
     :then
     (insert! :chara/ubim
              (merge
               (if (> anim-elapsed-ms 100)
                 {::anim-tick (inc anim-tick) ::anim-elapsed-ms 0}
                 {::anim-elapsed-ms (+ anim-elapsed-ms delta-time)})
               (case keystate
                 ::input/keydown
                 (let [pingpong (case (mod anim-tick 4) 0 -1 1 0 2 1 3 0)]
                   {::sprites/frame-index (- (case keyname ::input/down 1 ::input/left 13 ::input/right 25 ::input/up 37 1) pingpong)})

                 ::input/keyup
                 {::anim-elapsed-ms 0
                  ::sprites/frame-index (case keyname ::input/down 1 ::input/left 13 ::input/right 25 ::input/up 37 1)}

                 {})))]}))