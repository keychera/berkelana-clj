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
   [rules.time :as time]
   [engine.world :as world]))

; init
(s/def ::move-duration number?)
(s/def ::pos-x number?)
(s/def ::pos-y number?)
(def default {::state ::init ::move-duration 100 ::prev-x 0 ::prev-y 0 ::move-delay 1})

; properties
(s/def ::unwalkable? boolean?)
(s/def ::pushable? boolean?)

; intermediates
(s/def ::prev-x number?)
(s/def ::prev-y number?)
(s/def ::next-x number?)
(s/def ::next-y number?)
(s/def ::move-x number?)
(s/def ::move-y number?)
(s/def ::move-delay number?)

(s/def ::state #{::init ::idle ::deciding-move})
(s/def ::check-order int?)

(def sp->layers->props (sp/path [:id/worldmap ::tiled/tiled-map :layers (sp/multi-path "Structures" "Interactables")]))
(def sp->map-dimension (sp/multi-path [:id/worldmap ::tiled/tiled-map :map-width]
                                      [:id/worldmap ::tiled/tiled-map :map-height]))

(def grid 16)
(world/system system
  {::world/rules
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
                      ::pos2d/x (* grid pos-x)
                      ::pos2d/y (* grid pos-x)}))]

     ::move-ubim
     [:what
      [::time/now ::time/delta delta-time]
      [keyname ::input/pressed-key ::input/keydown]
      [esse-id ::state ::idle {:then false}]
      [esse-id ::pos-x pos-x {:then false}]
      [esse-id ::pos-y pos-y {:then false}]
      [esse-id ::move-delay move-delay {:then false}]
      :when
      (= esse-id :chara/ubim)
      (<= move-delay 0)
      (#{:left :right :up :down} keyname)
      :then
      (let [move-x (case keyname :left -1 :right 1 0)
            move-y (case keyname :up   -1 :down  1 0)]
        (s-> session
             (o/insert esse-id {::state ::deciding-move ::check-order 0 ::move-x move-x ::move-y move-y})))]

     ::move-esse
     [:what
      [esse-id ::state ::deciding-move]
      [esse-id ::check-order 0 {:then false}]
      [esse-id ::move-x move-x]
      [esse-id ::move-y move-y]
      [esse-id ::pos-x pos-x {:then false}]
      [esse-id ::pos-y pos-y {:then false}]
      :when
      (or (not= 0 move-x) (not= 0 move-y))
      :then
      (s-> session (o/insert esse-id {::prev-x pos-x ::prev-y pos-y ::next-x (+ pos-x move-x) ::next-y (+ pos-y move-y) ::check-order 1}))]

     ::check-world-boundaries
     [:what
      [esse-id ::state ::deciding-move]
      [esse-id ::check-order 1 {:then false}]
      [::asset/global ::asset/db* db*]
      [esse-id ::next-x next-x {:then false}]
      [esse-id ::next-y next-y {:then false}]
      :then
      (let [db @db*
            out-of-map? (let [[map-width map-height] (sp/select sp->map-dimension db)]
                          (and map-width map-height
                               (or (< next-x 0) (< next-y 0) (> next-x (dec map-width)) (> next-y (dec map-height)))))
            unwalkable?   (or (sp/select-one [sp->layers->props next-x next-y some? ::tiled/props :unwalkable] db) false)]
        (if (or out-of-map? unwalkable?)
          (s-> session (o/insert esse-id {::state ::idle}))
          (s-> session (o/insert esse-id {::check-order 2}))))]

     ::check-unwalkable
     [:what
      [esse-id ::state ::deciding-move]
      [esse-id ::check-order 1 {:then false}]
      [esse-id ::next-x next-x {:then false}]
      [esse-id ::next-y next-y {:then false}]
      [blocker-id ::pos-x blocker-x {:then false}]
      [blocker-id ::pos-y blocker-y {:then false}]
      [blocker-id ::unwalkable? true {:then false}]
      :then
      (if (and (= next-x blocker-x) (= next-y blocker-y))
        (s-> session (o/insert esse-id {::state ::idle}))
        (s-> session (o/insert esse-id {::check-order 2})))]

     ::check-pushable
     [:what
      [esse-id ::state ::deciding-move]
      [esse-id ::check-order 1 {:then false}]
      [esse-id ::next-x next-x {:then false}]
      [esse-id ::next-y next-y {:then false}]
      [esse-id ::move-x move-x {:then false}]
      [esse-id ::move-y move-y {:then false}]
      [pushable-id ::pos-x pushable-x {:then false}]
      [pushable-id ::pos-y pushable-y {:then false}]
      [pushable-id ::pushable? true {:then false}]
      :then
      (if (and (= next-x pushable-x) (= next-y pushable-y))
        (s-> session
             (o/insert pushable-id {::state ::deciding-move ::check-order 0 ::move-x move-x ::move-y move-y}))
        (s-> session (o/insert esse-id {::check-order 2})))]

     ::allow-movement
     [:what
      [esse-id ::state ::deciding-move]
      [esse-id ::check-order 2]
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
                        ::pos2d/x (* grid x)
                        ::pos2d/y (* grid y)})))]})})

(comment
  (-> (->> (concat (::world/rules system)
                   (o/ruleset
                    {::current-pos
                     [:what
                      [esse-id ::pos-x pos-x]
                      [esse-id ::pos-y pos-y]]}))
           (reduce o/add-rule (o/->session)))
      (o/insert ::asset/global ::asset/db* (atom (sp/setval sp->map-dimension 100 {})))
      (o/insert "rock"   (merge default {::state ::init ::pos-x 2 ::pos-y 5 ::unwalkable? true}))
      (o/insert "bucket" (merge default {::state ::init ::pos-x 2 ::pos-y 3 ::pushable? true}))
      (o/insert "ubim"   (merge default {::state ::init ::pos-x 2 ::pos-y 2}))
      (o/fire-rules)
      (o/insert "ubim"   {::state ::deciding-move ::check-order 0 ::move-x 0 ::move-y 1})
      (o/fire-rules)
      (o/insert "ubim"   {::state ::deciding-move ::check-order 0 ::move-x 0 ::move-y 1})
      (o/fire-rules)
      (o/query-all ::current-pos)))