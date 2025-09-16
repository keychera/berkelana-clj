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
         (o/insert ::world/global ::mode ::mode-movement)))

   ::world/rules
   (o/ruleset
    {::global-mode
     [:what
      [::time/now ::time/delta delta-time]
      [input-id ::input/pressed-key keystate]
      :then
      (let [keyname (keyword (name input-id))]
        (cm/match [keyname keystate]
          [(:or :r) ::input/keydown]
          (s-> session
               (o/insert ::world/global ::mode ::mode-movement)
               (o/insert ::world/global ::world/control :reset)
               (o/retract input-id ::input/pressed-key))

          :else :no-op))]

     ::mode-movement
     [:what
      [::time/now ::time/delta delta-time]
      [input-id ::input/pressed-key keystate]
      [::world/global ::mode ::mode-movement {:then false}]
      :then
      (let [keyname (keyword (name input-id))]
        (cm/match [keyname keystate]
          [(:or :up :down :right :left) ::input/keydown]
          (s-> session
               (o/insert :chara/ubim ::grid-move/control keyname))
          [(:or :up :down :right :left) ::input/keyup]
          (s-> session
               (o/insert :chara/ubim ::grid-move/control :idle)
               (o/retract input-id ::input/pressed-key))

          [(:or :space) ::input/keydown]
          (s-> session
               (o/insert ::world/global ::mode ::mode-dialogue)
               (o/insert ::dialogues/this ::dialogues/control ::dialogues/trigger))

          :else :no-op))]

     ::mode-dialogue
     [:what
      [::time/now ::time/delta delta-time]
      [input-id ::input/pressed-key keystate]
      [::dialogues/this ::dialogues/test-counter counter]
      [::world/global ::mode ::mode-dialogue {:then false}]
      :then
      (let [keyname (keyword (name input-id))]
        (cm/match [counter keyname keystate]
          [1 (:or :space) ::input/keydown]
          (s-> session
               (o/insert ::dialogues/this ::dialogues/control ::dialogues/end)
               (o/insert ::world/global ::mode ::mode-movement)
               (o/retract input-id ::input/pressed-key))

          [_ (:or :space) ::input/keydown]
          (s-> session
               (o/insert ::world/global ::mode ::mode-dialogue)
               (o/insert ::dialogues/this ::dialogues/control ::dialogues/trigger)
               (o/retract input-id ::input/pressed-key))

          :else :no-op))]})})