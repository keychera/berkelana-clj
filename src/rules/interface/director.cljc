(ns rules.interface.director
  (:require
   [clojure.core.match :as cm]
   [clojure.spec.alpha :as s]
   [engine.macros :refer [s->]]
   [engine.world :as world]
   [odoyle.rules :as o]
   [rules.dev.dev-only :as dev-only]
   [rules.dialogues :as dialogues]
   [rules.grid-move :as grid-move]
   [rules.interface.input :as input]
   [rules.time :as time]))

(s/def ::mode keyword?)

(world/system system
  {::world/init-fn
   (fn [world _game]
     (-> world
         (o/insert ::world/global ::mode ::in-movement)))

   ::world/rules
   (o/ruleset
    {::input-mode
     [:what
      [::time/now ::time/delta delta-time]
      [input-id ::input/pressed-key keystate]
      [::world/global ::mode mode {:then false}]
      [::world/global ::world/game game {:then false}]
      :then
      (let [keyname (keyword (name input-id))]
        (cm/match [mode keyname keystate]
          [_ (:or :r) ::input/keydown]
          (s-> session
               (o/insert ::world/global ::mode ::in-movement)
               (o/insert ::dialogues/this ::mode ::in-movement)
               (o/insert ::world/global ::world/control :reset)
               (o/retract input-id ::input/pressed-key))

          [::in-movement (:or :up :down :right :left) ::input/keydown]
          (s-> session
               (o/insert :chara/ubim ::grid-move/control keyname))
          [::in-movement (:or :up :down :right :left) ::input/keyup]
          (s-> session
               (o/insert :chara/ubim ::grid-move/control :idle)
               (o/retract input-id ::input/pressed-key))

          [(:or ::in-movement ::in-dialogue) (:or :space) ::input/keydown]
          (s-> session
               (o/insert ::world/global ::mode ::in-dialogue)
               (o/insert ::dialogues/this ::dialogues/control :progress))

          :else
          (s-> session
               (dev-only/inspect-session "not received" keyname))))]})})