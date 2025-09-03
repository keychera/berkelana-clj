(ns rules.grid-move
  (:require
   #?(:clj [engine.macros :refer [s->]]
      :cljs [engine.macros :refer-macros [s->]])
   [assets.assets :as asset]
   [assets.tiled :as tiled]
   [clojure.spec.alpha :as s]
   [com.rpl.specter :as sp]
   [odoyle.rules :as o]
   [rules.input :as input]
   [rules.pos2d :as pos2d]
   [rules.time :as time]))

; init
(s/def ::move-duration number?)
(s/def ::pos-x number?)
(s/def ::pos-y number?)
(def default {::state ::idle ::move-duration 100 ::prev-x 0 ::prev-y 0 ::move-delay 1})

; properties
(s/def ::unwalkable? boolean?)

; intermediates
(s/def ::prev-x number?)
(s/def ::prev-y number?)
(s/def ::next-x number?)
(s/def ::next-y number?)
(s/def ::move-delay number?)

(s/def ::state #{::init ::idle ::deciding-move})
(s/def ::check-order int?)

(def sp->layers->props (sp/path [:id/worldmap ::tiled/tiled-map :layers (sp/multi-path "Structures" "Interactables")]))
(def sp->map-dimension (sp/multi-path [:id/worldmap ::tiled/tiled-map :map-width]
                                      [:id/worldmap ::tiled/tiled-map :map-height]))

(def grid 16)
(def offset 0) ;; to align with tiledmap grid

(def rules
  (o/ruleset
   {::position-on-init
    [:what
     [esse-id ::state ::init]
     [esse-id ::pos-x pos-x {:then false}]
     [esse-id ::pos-y pos-y {:then false}]
     :then
     (s-> session
          (o/insert esse-id
                    {::state ::idle
                     ::pos2d/x (- (* grid pos-x) offset)
                     ::pos2d/y (- (* grid pos-y) offset)}))]
    
    ::move-player
    [:what
     [::time/now ::time/delta delta-time]
     [keyname ::input/pressed-key ::input/keydown]
     [esse-id ::pos-x pos-x {:then false}]
     [esse-id ::pos-y pos-y {:then false}]
     [esse-id ::move-delay move-delay {:then false}]
     :when
     (= esse-id :chara/ubim)
     (<= move-delay 0)
     (#{:left :right :up :down} keyname)
     :then
     (let [next-x (case keyname :left (dec pos-x) :right (inc pos-x) pos-x)
           next-y (case keyname :up   (dec pos-y) :down  (inc pos-y) pos-y)]
       (s-> session
            (o/insert esse-id {::prev-x pos-x ::prev-y pos-y ::next-x next-x ::next-y next-y
                               ::state ::deciding-move ::check-order 0})))]

    ::check-world-boundaries
    [:what
     [esse-id ::state ::deciding-move]
     [::asset/global ::asset/db* db*]
     [esse-id ::next-x next-x {:then false}]
     [esse-id ::next-y next-y {:then false}]
     [esse-id ::check-order 0 {:then false}]
     :then
     (let [db @db*
           out-of-map? (let [[map-width map-height] (sp/select sp->map-dimension db)]
                         (or (< next-x 0) (< next-y 0) (> next-x (dec map-width)) (> next-y (dec map-height))))
           unwalkable?   (sp/select-one [sp->layers->props next-x next-y some? ::tiled/props :unwalkable] db)]
       (if (or out-of-map? unwalkable?)
         (s-> session (o/insert esse-id {::state ::idle}))
         (s-> session (o/insert esse-id {::check-order 1}))))]

    ::check-unwalkable-object
    [:what
     [esse-id ::state ::deciding-move]
     [esse-id ::next-x next-x {:then false}]
     [esse-id ::next-y next-y {:then false}]
     [blocker-id ::pos-x blocker-x {:then false}]
     [blocker-id ::pos-y blocker-y {:then false}]
     [blocker-id ::unwalkable? unwalkable? {:then false}]
     [esse-id ::check-order 0 {:then false}]
     :then
     (if (and unwalkable? (= next-x blocker-x) (= next-y blocker-y))
       (s-> session (o/insert esse-id {::state ::idle}))
       (s-> session (o/insert esse-id {::check-order 1})))]

    ::allow-movement
    [:what
     [esse-id ::state ::deciding-move]
     [esse-id ::check-order 1]
     [esse-id ::next-x next-x {:then false}]
     [esse-id ::next-y next-y {:then false}]
     [esse-id ::move-duration move-duration {:then false}]
     :then
     (s-> session (o/insert esse-id {::state ::idle ::pos-x next-x ::pos-y next-y ::move-delay move-duration}))]

    ::animate-pos
    [:what
     [::time/now ::time/delta delta-time]
     [esse-id ::pos-x px]
     [esse-id ::pos-y py]
     [esse-id ::prev-x sx]
     [esse-id ::prev-y sy]
     [esse-id ::move-delay move-delay {:then false}]
     [esse-id ::move-duration move-duration {:then false}]
     :when (> move-delay 0)
     :then
     (let [t (- 1.0 (/ move-delay move-duration))
           ease-fn identity
           x (+ sx (* (- px sx) (ease-fn t)))
           y (+ sy (* (- py sy) (ease-fn t)))]
       (s-> session
            (o/insert esse-id
                      {::move-delay (- move-delay delta-time)
                       ::pos2d/x (- (* grid x) offset)
                       ::pos2d/y (- (* grid y) offset)})))]}))

(s/def ::did-move? boolean?)

(comment
  (-> (->> (o/ruleset
            {::current-pos
             [:what
              [esse-id ::pos-x pos-x]
              [esse-id ::pos-y pos-y]
              [esse-id ::did-move? did-move?]]

             ::state
             [:what
              [esse-id ::prev-x prev-x {:then false}]
              [esse-id ::prev-y prev-y {:then false}]
              [esse-id ::next-x next-x]
              [esse-id ::next-y next-y]
              :then
              (s-> session (o/insert esse-id {::state match ::check-order 0}))]

             ::check-unwalkable-object
             [:what
              [esse-id ::state move-decision]
              [esse-id ::check-order 0]
              [blocker-id ::pos-x blocker-x {:then false}]
              [blocker-id ::pos-y blocker-y {:then false}]
              [blocker-id ::unwalkable? unwalkable? {:then false}]
              :then
              (let [{:keys [prev-x prev-y next-x next-y]} move-decision]
                (println "next is unwalkable?" unwalkable?)
                (if (not unwalkable?)
                  (s-> session
                       (o/insert esse-id {::pos-x next-x ::pos-y next-y ::check-order 1}))
                  (s-> session
                       (o/retract esse-id ::state)
                       (o/insert esse-id {::pos-x prev-x ::pos-y prev-y}))))]

             ::check-world-boundaries
             [:what
              [esse-id ::state move-decision]
              [esse-id ::check-order 0]
              :then
              (let [{:keys [prev-x prev-y next-x next-y]} move-decision]
                (println "inside map?" (and (< next-x 2) (< next-y 2)))
                (if (and (< next-x 2) (< next-y 2))
                  (s-> session (o/insert esse-id {::pos-x next-x ::pos-y next-y ::check-order 1}))
                  (s-> session
                       (o/retract esse-id ::state)
                       (o/insert esse-id {::pos-x prev-x ::pos-y prev-y}))))]

             ::allow-movement
             [:what
              [esse-id ::state move-decision]
              [esse-id ::check-order 1]
              :then
              (let [{:keys [next-x next-y]} move-decision]
                (s-> session
                     (o/retract esse-id ::state)
                     (o/insert esse-id {::pos-x next-x ::pos-y next-y ::did-move? true})))]})

           (reduce o/add-rule (o/->session)))
      (o/insert "obstacle" {::pos-x 1 ::pos-y 1 ::unwalkable? false})
      (o/insert "player" {::prev-x 0 ::prev-y 0 ::next-x 1 ::next-y 1})
      (o/fire-rules)
      (o/query-all ::current-pos)))