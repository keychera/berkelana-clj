(ns rules.grid-move
  (:require
   [assets.assets :as asset]
   [assets.tiled :as tiled]
   [clojure.spec.alpha :as s]
   [com.rpl.specter :as sp]
   [engine.macros :refer [s-> insert!]]
   [engine.world :as world]
   [odoyle.rules :as o]
   [rules.input :as input]
   [rules.pos2d :as pos2d]
   [rules.time :as time]))

; init
(s/def ::move-duration number?)
(s/def ::pos-x number?)
(s/def ::pos-y number?)
(def default {::move-duration 80 ::move-delay 0 ::move-x 0 ::move-y 0 ::facing :down})

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
(s/def ::move-state #{::idle ::teleport ::plan-move ::check-world-boundaries ::check-unwalkable ::check-pushable ::allow-push ::allow-move ::prevent-move ::moving})
(s/def ::pushing any?)

;; ubim-only, this is grounds for refactor
(s/def ::facing keyword?)

(def sp->layers->props (sp/path [:id/worldmap ::tiled/tiled-map :layers (sp/multi-path "Structures" "Interactables" "Terrain")]))
(def sp->map-dimension (sp/multi-path [:id/worldmap ::tiled/tiled-map :map-width]
                                      [:id/worldmap ::tiled/tiled-map :map-height]))

(def grid 16)
(world/system system
  {::world/rules
   (o/ruleset
    {::position-on-init
     [:what
      [esse-id ::pos-x _ {:then false}] ;; assuming having this fact means esse is using grid-move
      [:id/worldmap ::asset/loaded? true]
      :then
      (s-> session (o/insert esse-id ::move-state ::teleport))]

     ::teleport
     [:what
      [esse-id ::pos-x pos-x {:then false}]
      [esse-id ::pos-y pos-y {:then false}]
      [esse-id ::move-state ::teleport]
      :then
      (s-> session (o/insert esse-id {::move-state ::idle ::pos2d/x (* grid pos-x) ::pos2d/y (* grid pos-y)}))]

     ::move-ubim
     [:what
      [::time/now ::time/delta delta-time]
      [keyname ::input/pressed-key ::input/keydown]
      [esse-id ::pos-x pos-x {:then false}]
      [esse-id ::pos-y pos-y {:then false}]
      [esse-id ::move-delay move-delay {:then false}]
      :when
      (= esse-id :chara/ubim)
      (<= move-delay 0)
      (#{::input/left ::input/right ::input/up ::input/down} keyname)
      :then
      (let [move-x (case keyname ::input/left -1 ::input/right 1 0)
            move-y (case keyname ::input/up   -1 ::input/down  1 0)]
        (insert! esse-id {::facing (keyword (name keyname))
                          ::move-state ::plan-move ::move-x move-x ::move-y move-y}))]

     ::plan-move
     [:what
      [esse-id ::move-state ::plan-move]
      [esse-id ::move-x move-x]
      [esse-id ::move-y move-y]
      [esse-id ::pos-x pos-x {:then false}]
      [esse-id ::pos-y pos-y {:then false}]
      :when
      (or (not= 0 move-x) (not= 0 move-y))
      :then
      (s-> session (o/insert esse-id {::prev-x pos-x ::prev-y pos-y ::next-x (+ pos-x move-x) ::next-y (+ pos-y move-y) ::move-state ::check-world-boundaries}))]

     ::check-world-boundaries
     [:what
      [esse-id ::move-state ::check-world-boundaries]
      [::asset/global ::asset/db* db*]
      [esse-id ::next-x next-x {:then false}]
      [esse-id ::next-y next-y {:then false}]
      :then
      (let [db @db*
            out-of-map? (let [[map-width map-height] (sp/select sp->map-dimension db)]
                          (and map-width map-height
                               (or (< next-x 0) (< next-y 0) (> next-x (dec map-width)) (> next-y (dec map-height)))))
            unwalkable?   (or (sp/select-any [sp->layers->props next-x next-y some? ::tiled/props :unwalkable] db) false)]
        (if (or out-of-map? unwalkable?)
          (s-> session (o/insert esse-id {::move-state ::prevent-move}))
          (s-> session (o/insert esse-id {::move-state ::check-unwalkable}))))]

     ::unwalkable-esse
     [:what
      [unwalkable-id ::pos-x unwalkable-x]
      [unwalkable-id ::pos-y unwalkable-y]
      [unwalkable-id ::unwalkable? true]]

     ::check-unwalkable
     [:what
      [esse-id ::move-state ::check-unwalkable]
      [esse-id ::next-x next-x {:then false}]
      [esse-id ::next-y next-y {:then false}]
      :then
      (let [unwalkable-esses
            (some->> (o/query-all session ::unwalkable-esse)
                     (filter #(and (= next-x (:unwalkable-x %)) (= next-y (:unwalkable-y %))))
                     (map :unwalkable-id))]
        (if (seq unwalkable-esses)
          (s-> session (o/insert esse-id {::move-state ::prevent-move}))
          (s-> session (o/insert esse-id {::move-state ::check-pushable}))))]

     ::pushable-esse
     [:what
      [pushable-id ::pos-x pushable-x]
      [pushable-id ::pos-y pushable-y]
      [pushable-id ::pushable? true]]

     ::check-pushable
     [:what
      [esse-id ::move-state ::check-pushable]
      [esse-id ::next-x next-x {:then false}]
      [esse-id ::next-y next-y {:then false}]
      [esse-id ::move-x move-x {:then false}]
      [esse-id ::move-y move-y {:then false}]
      :then
      (let [pushable-esses
            (some->> (o/query-all session ::pushable-esse)
                     (filter #(and (= next-x (:pushable-x %)) (= next-y (:pushable-y %))))
                     (map :pushable-id))]
        (if (seq pushable-esses)
          (s-> (reduce
                (fn [session' pushable-id]
                  (-> session'
                      (o/insert esse-id {::pushing pushable-id})
                      (o/insert pushable-id {::move-state ::plan-move ::move-x move-x ::move-y move-y})))
                session
                pushable-esses))
          (s-> session (o/insert esse-id {::move-state ::allow-move}))))]

     ::allow-push
     [:what
      [esse-id ::pushing pushable-id]
      [pushable-id ::move-state pushable-move-plan]
      :when (#{::allow-move ::prevent-move} pushable-move-plan)
      :then
      (case pushable-move-plan
        ::allow-move
        (s-> session (o/insert esse-id {::move-state ::allow-move ::pushing nil}))

        ::prevent-move
        (s-> session (o/insert esse-id {::move-state ::prevent-move ::pushing nil})))]

     ::allow-move
     [:what
      [esse-id ::move-state move-plan]
      [esse-id ::prev-x prev-x]
      [esse-id ::prev-y prev-y]
      [esse-id ::next-x next-x {:then false}]
      [esse-id ::next-y next-y {:then false}]
      [esse-id ::move-duration move-duration {:then false}]
      :when (#{::allow-move ::prevent-move} move-plan)
      :then
      (case move-plan
        ::allow-move
        (s-> session (o/insert esse-id {::move-state ::moving ::pos-x next-x ::pos-y next-y ::move-delay move-duration}))

        ::prevent-move ;; is just moving but with position unchanged, done like this so rules.ubim animation can still animate itself
        (s-> session (o/insert esse-id {::move-state ::moving ::next-x prev-x ::next-y prev-y ::move-delay move-duration})))]

     ::animate-pos
     [:what
      [::time/now ::time/delta delta-time]
      [esse-id ::move-state ::moving]
      [esse-id ::pos-x px]
      [esse-id ::pos-y py]
      [esse-id ::prev-x sx]
      [esse-id ::prev-y sy]
      [esse-id ::move-delay move-delay {:then false}]
      [esse-id ::move-duration move-duration {:then false}]
      :then
      (if (> move-delay 0)
        (let [t (- 1.0 (/ move-delay move-duration))
              ease-fn identity
              move-delay (- move-delay delta-time)
              x (if (> move-delay 0) (+ sx (* (- px sx) (ease-fn t))) px)
              y (if (> move-delay 0) (+ sy (* (- py sy) (ease-fn t))) py)]
          (s-> session
               (o/insert esse-id
                         {::move-delay move-delay
                          ::pos2d/x (* grid x)
                          ::pos2d/y (* grid y)})))
        (insert! esse-id {::move-state ::idle ::move-x 0 ::move-y 0}))]})})

(comment
  (-> (->> (concat (::world/rules system)
                   (o/ruleset
                    {::current-pos
                     [:what
                      [esse-id ::pos-x pos-x]
                      [esse-id ::pos-y  pos-y]]}))
           (reduce o/add-rule (o/->session)))
      (o/insert ::asset/global ::asset/db* (atom (sp/setval sp->map-dimension 100 {})))
      (o/insert "rock"   (merge default {::pos-x 2 ::pos-y 5 ::unwalkable? true}))
      (o/insert "bucket" (merge default {::pos-x 2 ::pos-y 3 ::pushable? true}))
      (o/insert "ubim"   (merge default {::pos-x 2 ::pos-y 2}))
      (o/fire-rules)
      (o/insert "ubim"   {::move-state ::plan-move ::move-x 0 ::move-y 1})
      (o/fire-rules)
      (o/insert "ubim"   {::move-state ::plan-move ::move-x 0 ::move-y 1})
      (o/fire-rules)
      (o/query-all ::current-pos)))