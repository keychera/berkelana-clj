(ns rules.ubim
  (:require
   #?(:clj [engine.macros :refer [insert!]]
      :cljs [engine.macros :refer-macros [insert!]])
   [clojure.spec.alpha :as s]
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
     [:ubim ::anim-tick anim-tick {:then false}]
     [:ubim ::anim-elapsed-ms anim-elapsed-ms {:then false}]
     :when
     (#{:left :right :up :down} keyname)
     :then
     (insert! :ubim
              (merge
               (if (> anim-elapsed-ms 100)
                 {::anim-tick (inc anim-tick) ::anim-elapsed-ms 0}
                 {::anim-elapsed-ms (+ anim-elapsed-ms delta-time)})
               (case keystate
                 ::input/keydown
                 (let [pingpong (case (mod anim-tick 4) 0 -1 1 0 2 1 3 0)]
                   {::sprites/frame-index (- (case keyname :down 1 :left 13 :right 25 :up 37 1) pingpong)})

                 ::input/keyup
                 {::anim-elapsed-ms 0
                  ::sprites/frame-index (case keyname :down 1 :left 13 :right 25 :up 37 1)}

                 {})))]}))