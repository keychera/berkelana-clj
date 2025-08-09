(ns rules.grid-move
  (:require
   #?(:clj [engine.macros :refer [insert!]]
      :cljs [engine.macros :refer-macros [insert!]])
   [clojure.spec.alpha :as s]
   [odoyle.rules :as o]
   [rules.input :as input]
   [rules.time :as time]))

; init
(s/def ::target-attr-x keyword?)
(s/def ::target-attr-y keyword?)
(s/def ::move-duration number?)
(s/def ::pos-x number?)
(s/def ::pos-y number?)
(def default {::pos-x 0 ::pos-y 0 ::move-duration 100})

; intermediates
(s/def ::prev-x number?)
(s/def ::prev-y number?)
(s/def ::move-delay number?)

(def rules
  (o/ruleset
   {::configure
    [:what
     [esse-id ::target-attr-x attr-x]
     [esse-id ::target-attr-y attr-y]
     :then
     (insert! esse-id {::prev-x 0 ::prev-y 0 ::move-delay 0})]

    ::move-player
    [:what
     [esse-id ::target-attr-x attr-x]
     [esse-id ::target-attr-y attr-y]
     [::time/now ::time/delta delta-time]
     [keyname ::input/pressed-key ::input/keydown]
     [esse-id ::pos-x pos-x {:then false}]
     [esse-id ::pos-y pos-y {:then false}]
     [esse-id ::move-delay move-delay {:then false}]
     [esse-id ::move-duration move-duration {:then false}]
     :when
     (<= move-delay 0)
     (#{:left :right :up :down} keyname)
     :then
     (insert! esse-id
              {::prev-x pos-x
               ::prev-y pos-y
               ::pos-x (case keyname :left (dec pos-x) :right (inc pos-x) pos-x)
               ::pos-y (case keyname :up (dec pos-y) :down (inc pos-y) pos-y)
               ::move-delay move-duration})]

    ::move-delay
    [:what
     [esse-id ::target-attr-x attr-x]
     [esse-id ::target-attr-y attr-y]
     [::time/now ::time/delta delta-time]
     [esse-id ::move-delay move-delay {:then false}]
     :when (> move-delay 0)
     :then
     (insert! esse-id {::move-delay (- move-delay delta-time)})]

    ::animate-pos
    [:what
     [esse-id ::target-attr-x attr-x]
     [esse-id ::target-attr-y attr-y]
     [esse-id ::pos-x px]
     [esse-id ::pos-y py]
     [esse-id ::prev-x sx]
     [esse-id ::prev-y sy]
     [esse-id ::move-delay move-delay]
     [esse-id ::move-duration move-duration {:then false}]
     :then
     (let [t (- 1.0 (/ move-delay move-duration))
           ease-fn #(Math/pow % 2) grid 64
           x (+ sx (* (- px sx) (ease-fn t)))
           y (+ sy (* (- py sy) (ease-fn t)))]
       (insert! esse-id
                {attr-x (* grid x)
                 attr-y (* grid y)}))]}))
