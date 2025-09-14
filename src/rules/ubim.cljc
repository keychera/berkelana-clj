(ns rules.ubim
  (:require
   [clojure.core.match :as cm]
   [clojure.spec.alpha :as s]
   [engine.macros :refer [insert!]]
   [odoyle.rules :as o]
   [rules.grid-move :as grid-move]
   [rules.sprites :as sprites]
   [rules.time :as time]))

(s/def ::anim-tick number?)
(s/def ::anim-elapsed-ms number?)
(s/def ::facing #{:idle :up :down :left :right})

(def rules
  (o/ruleset
   {::listen-to-grid-move
    [:what
     [esse-id ::grid-move/move-state ::grid-move/plan-move]
     [esse-id ::grid-move/move-x move-x]
     [esse-id ::grid-move/move-y move-y]
     :then
     (insert! :chara/ubim ::facing
              (cm/match [move-x move-y]
                [_  1] :down
                [-1 _] :left
                [1  _] :right
                [_ -1] :up))]

    ::ubim-animation
    [:what
     [::time/now ::time/delta delta-time]
     [:chara/ubim ::facing direction]
     [:chara/ubim ::grid-move/move-state move-state]
     [:chara/ubim ::anim-tick anim-tick {:then false}]
     [:chara/ubim ::anim-elapsed-ms anim-elapsed-ms {:then false}]
     :then
     (insert! :chara/ubim
              (merge
               (if (> anim-elapsed-ms 100)
                 {::anim-tick (inc anim-tick) ::anim-elapsed-ms 0}
                 {::anim-elapsed-ms (+ anim-elapsed-ms delta-time)})
               (let [pingpong (case (mod anim-tick 4) 0 -1 1 0 2 1 3 0)]
                 {::sprites/frame-index
                  (cond-> (case direction :down 1 :left 13 :right 25 :up 37 1)
                    (not= ::grid-move/idle move-state) (- pingpong))})))]}))