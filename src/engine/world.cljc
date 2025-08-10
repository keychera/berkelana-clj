(ns engine.world
  (:require
   #?(:cljs [rules.dev.leva-rules :as leva-rules])
   #?(:clj [engine.macros :refer [insert!]]
      :cljs [engine.macros :refer-macros [insert!]])
   [clojure.spec.alpha :as s]
   [odoyle.rules :as o]
   [rules.asset.asset :as asset]
   [rules.asset.spritesheet :as spritesheet]
   [rules.dev.dev-only :as dev-only]
   [rules.esse :as esse]
   [rules.grid-move :as grid-move]
   [rules.input :as input]
   [rules.shader :as shader]
   [rules.time :as time]))

(defonce world* (atom nil))

(defn rules-debugger-wrap-fn [rule]
  (o/wrap-rule rule
               {:what
                (fn [f session new-fact old-fact]
                  (when (#{} (:name rule))
                    (println (:name rule) "is comparing" old-fact "=>" new-fact))
                  (f session new-fact old-fact))
                :when
                (fn [f session match]
                  (when (#{} (:name rule))
                    (println "when" (:name rule)))
                  (f session match))
                :then
                (fn [f session match]
                  (when (#{} (:name rule))
                    (println "firing" (:name rule) "for" (select-keys match [:esse-id])))
                  (f session match))
                :then-finally
                (fn [f session]
                  ;; (println :then-finally (:name rule))
                  (f session))}))

(def rules
  (o/ruleset
   {::window
    [:what
     [::window ::width width]
     [::window ::height height]]

    ::move-animation
    [:what
     [::time/now ::time/delta delta-time]
     [keyname ::input/pressed-key keystate]
     [:ubim ::esse/anim-tick anim-tick {:then false}]
     [esse-id ::esse/anim-elapsed-ms anim-elapsed-ms {:then false}]
     [esse-id ::esse/frame-index frame-index {:then false}]
     :when
     (#{:left :right :up :down} keyname)
     :then
     (insert! esse-id
              (merge
               (if (> anim-elapsed-ms 100)
                 {::esse/anim-tick (inc anim-tick) ::esse/anim-elapsed-ms 0}
                 {::esse/anim-elapsed-ms (+ anim-elapsed-ms delta-time)})
               (case keystate
                 ::input/keydown
                 (let [pingpong (case (mod anim-tick 4) 0 -1 1 0 2 1 3 0)]
                   {::esse/frame-index (- (case keyname :down 1 :left 13 :right 25 :up 37 1) pingpong)})
                 ::input/keyup
                 {::esse/anim-elapsed-ms 0
                  ::esse/frame-index (case keyname :down 1 :left 13 :right 25 :up 37 1)}
                 {})))]

    ::sprite-esse
    [:what
     [esse-id ::esse/x x]
     [esse-id ::esse/y y]
     [esse-id ::esse/frame-index frame-index]
     [esse-id ::esse/sprite-from-asset asset-id]
     [asset-id ::asset/loaded? true]]}))

(defonce ^:devonly previous-rules (atom nil))

(defn deep-merge [a & maps]
  (if (map? a)
    (apply merge-with deep-merge a maps)
    (apply merge-with deep-merge maps)))

(defn esse
  [session id & maps]
  (o/insert session id (apply deep-merge maps)))

(defn init-world [session]
  (let [all-rules (concat rules
                          time/rules
                          input/rules
                          asset/rules
                          spritesheet/rules
                          grid-move/rules
                          shader/rules
                          #?(:cljs leva-rules/rules)
                          dev-only/rules)
        init-only? (nil? session)
        session (if init-only?
                  (o/->session)
                  (->> @previous-rules ;; devonly : refresh rules without resetting facts
                       (map :name)
                       (reduce o/remove-rule session)))]
    (reset! previous-rules all-rules)
    (-> (->> all-rules
             (map #'rules-debugger-wrap-fn)
             (reduce o/add-rule session))
        (cond-> init-only?
          (-> (esse :asset/char0
                    #::asset{:to-load "char0.png" :type ::asset/spritesheet}
                    #::spritesheet{:frame-width 32 :frame-height 32})))
        ;; if esse attributes are inserted partially it will not hit the rule and facts will be discarded
        (esse :john
              grid-move/default #::grid-move{:target-attr-x ::esse/x :target-attr-y ::esse/y :pos-x 2 :pos-y 2}
              #::esse{::shader/shader-to-load shader/->hati :x 0 :y 0})
        (esse :ubim
              grid-move/default #::grid-move{:target-attr-x ::esse/x :target-attr-y ::esse/y :pos-x 4 :pos-y 4}
              #::esse{:sprite-from-asset :asset/char0
                      :x 0 :y 0 :frame-index 0 :anim-tick 0 :anim-elapsed-ms 0}))))

;; specs
(s/def ::width number?)
(s/def ::height number?)

(comment
  (o/query-all @world* ::spritesheet/spritesheet))