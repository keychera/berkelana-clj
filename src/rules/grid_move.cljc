(ns rules.grid-move
  (:require
   #?(:clj [engine.macros :refer [insert! s->]]
      :cljs [engine.macros :refer-macros [s-> insert!]])
   [assets.assets :as asset]
   [assets.tiled :as tiled]
   [clojure.spec.alpha :as s]
   [com.rpl.specter :as sp]
   [odoyle.rules :as o]
   [rules.dev.dev-only :as dev-only]
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
(s/def ::next-x number?)
(s/def ::next-y number?)
(s/def ::move-delay number?)

(def sp->layers->props (sp/path [:id/worldmap ::tiled/tiled-map :layers (sp/multi-path "Structures" "Interactables")]))
(def sp->map-dimension (sp/multi-path [:id/worldmap ::tiled/tiled-map :map-width]
                                      [:id/worldmap ::tiled/tiled-map :map-height]))

(def offset 8) ;; to align with tiledmap grid

(def rules
  (o/ruleset
   {::configure
    [:what
     [esse-id ::target-attr-x attr-x]
     [esse-id ::target-attr-y attr-y]
     :then
     (insert! esse-id {::prev-x 0 ::prev-y 0 ::move-delay 0})]

    ::init-move-player
    [:what
     [esse-id ::target-attr-x attr-x]
     [esse-id ::target-attr-y attr-y]
     [::time/now ::time/delta delta-time]
     [keyname ::input/pressed-key ::input/keydown]
     [esse-id ::pos-x pos-x {:then false}]
     [esse-id ::pos-y pos-y {:then false}]
     [esse-id ::move-delay move-delay {:then false}]
     [::asset/global ::asset/db* db*]
     :when
     (<= move-delay 0)
     (#{:left :right :up :down} keyname)
     :then
     (let [next-x (case keyname :left (dec pos-x) :right (inc pos-x) pos-x)
           next-y (case keyname :up   (dec pos-y) :down  (inc pos-y) pos-y)
           unwalkable? (sp/select-one [sp->layers->props next-x next-y some? ::tiled/props :unwalkable] @db*)]
       (when (not unwalkable?)
         (s-> session
              (dev-only/send-dev-value [next-x next-y unwalkable?])
              (o/insert esse-id
                        {::prev-x pos-x ::prev-y pos-y ::next-x next-x ::next-y next-y}))))]

    ::check-map-boundary
    [:what
     [esse-id ::next-x next-x]
     [esse-id ::next-y next-y]
     [esse-id ::move-duration move-duration {:then false}]
     [::asset/global ::asset/db* db*]
     :when
     (let [[map-width map-height] (sp/select sp->map-dimension @db*)]
       (not (or (< next-x 0) (< next-y 0) (> next-x (dec map-width)) (> next-y (dec map-height)))))
     :then
     (s-> session (o/insert esse-id {::pos-x next-x ::pos-y next-y ::move-delay move-duration}))]

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
           ease-fn identity grid 16
           x (+ sx (* (- px sx) (ease-fn t)))
           y (+ sy (* (- py sy) (ease-fn t)))]
       (insert! esse-id
                {attr-x (- (* grid x) offset)
                 attr-y (- (* grid y) offset)}))]}))

