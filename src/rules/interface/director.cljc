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
         (o/insert ::world/global ::mode ::movement-mode)))

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
               (o/insert ::world/global ::mode ::movement-mode)
               (o/insert ::dialogues/this ::mode ::movement-mode)
               (o/insert ::world/global ::world/control :reset)
               (o/retract input-id ::input/pressed-key))

          :else :no-op))]

     ::movement-mode
     [:what
      [::time/now ::time/delta delta-time]
      [input-id ::input/pressed-key keystate]
      [::world/global ::mode ::movement-mode {:then false}]
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
               (o/insert ::world/global ::mode ::in-dialogue)
               (o/insert ::dialogues/this ::dialogues/control ::dialogues/trigger))

          :else :no-op))]

     ::dialogue-mode
     [:what
      [::time/now ::time/delta delta-time]
      [input-id ::input/pressed-key keystate]
      [::dialogues/this ::dialogues/test-counter counter]
      [::world/global ::mode ::in-dialogue {:then false}]
      :then
      (let [keyname (keyword (name input-id))]
        (cm/match [counter keyname keystate]
          [3 _ _]
          (s-> session
               (o/insert ::world/global ::mode ::in-movement))
          
          [_ (:or :space) ::input/keydown]
          (s-> session
               (o/insert ::world/global ::mode ::in-dialogue)
               (o/insert ::dialogues/this ::dialogues/control ::dialogues/trigger))

          :else :no-op))]})})