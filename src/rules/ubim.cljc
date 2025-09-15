(ns rules.ubim
  (:require
   [clojure.spec.alpha :as s]
   [engine.macros :refer [insert!]]
   [engine.world :as world]
   [odoyle.rules :as o]
   [rules.grid-move :as grid-move]
   [rules.room :as room]
   [rules.sprites :as sprites]
   [rules.time :as time]))

(s/def ::anim-tick number?)
(s/def ::anim-elapsed-ms number?)

(def rules
  (o/ruleset
   {::ubim-animation
    [:what
     [::time/now ::time/delta delta-time]
     [:chara/ubim ::grid-move/move-state move-state {:then false}]
     [:chara/ubim ::grid-move/facing facing {:then false}]
     [:chara/ubim ::anim-tick anim-tick {:then false}]
     [:chara/ubim ::anim-elapsed-ms anim-elapsed-ms {:then false}]
     :then
     (insert! :chara/ubim
              (merge
               (if (> anim-elapsed-ms 100)
                 {::anim-tick (inc anim-tick) ::anim-elapsed-ms 0}
                 {::anim-elapsed-ms (+ anim-elapsed-ms delta-time)})
               (if (= ::grid-move/idle move-state)
                 {::sprites/frame-index (case facing :down 1 :left 13 :right 25 :up 37 1)}
                 (let [pingpong (case (mod anim-tick 4) 0 -1 1 0 2 1 3 0)]
                   {::sprites/frame-index
                    (- (case facing :down 1 :left 13 :right 25 :up 37 1) pingpong)}))))]
    
    ::ubim-change-room
    [:what [::world/global ::room/currently-at room-id]
     :then (insert! :chara/ubim ::room/currently-at room-id)]}))