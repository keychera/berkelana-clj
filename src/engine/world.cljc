(ns engine.world
  (:require
   #?(:clj [engine.macros :refer [insert! s->]]
      :cljs [engine.macros :refer-macros [s-> insert!]])
   [clojure.spec.alpha :as s]
   [engine.esse :as esse]
   [odoyle.rules :as o]
   [rules.grid-move :as grid-move]
   [rules.shader :as shader]
   [rules.time :as time]
   [rules.input :as input]))

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

    ::leva-spritesheet
    [:what
     [::leva-spritesheet ::crop? crop?]
     [::leva-spritesheet ::frame leva-frame-index]
     [esse-id ::esse/frame-index _ {:then false}]
     :then
     (insert! esse-id ::esse/frame-index leva-frame-index)]

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
     [esse-id ::esse/current-sprite current-sprite]]

    ::sprite-ready
    [:what
     [esse-id ::esse/sprite-from-asset asset-id]
     [asset-id ::image-asset image]
     :then
     (s-> session
          (o/retract esse-id ::esse/sprite-from-asset)
          (o/insert esse-id ::esse/current-sprite image))]

    ::load-image
    [:what
     [asset-id ::asset-image-to-load image-path]]

    ::loading-image
    [:what
     [asset-id ::asset-image-to-load image-path]
     [asset-id ::image-loading? true]
     :then
     (s-> session (o/retract asset-id ::asset-image-to-load))]

    ::image-asset
    [:what
     [asset-id ::image-asset image]]}))

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
                          input/rules
                          grid-move/rules
                          shader/rules
                          time/rules)
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
          (-> (o/insert :asset/char0 ::asset-image-to-load "char0.png")))
        (o/insert ::leva-spritesheet ::crop? true)
        ;; if esse attributes are inserted partially it will not hit the rule and facts will be discarded
        (esse :john
              #::esse{::shader/shader-to-load shader/->hati :move-duration 100 :move-delay 0
                      :prev-x 4 :prev-y 4 :pos-x 4 :pos-y 4 :x 0 :y 0 :frame-index 0})
        (esse :ubim
              grid-move/default #::grid-move{:target-attr-x ::esse/x :target-attr-y ::esse/y :pos-x 4 :pos-y 4}
              #::esse{:sprite-from-asset :asset/char0 :move-duration 100
                      :x 0 :y 0
                      :frame-index 0 :move-delay 0 :anim-tick 0 :anim-elapsed-ms 0}))))

;; specs
(s/def ::width number?)
(s/def ::height number?)

(s/def ::pressed-key keyword?)

(s/def ::crop? boolean?)
(s/def ::frame int?)

(s/def ::asset-image-to-load string?)
(s/def ::image-loading? boolean?)
(s/def ::image-asset (s/nilable map?))

(comment
  (-> (->> (o/ruleset
            {::move-player
             [:what
              [keyname ::pressed-key ::keydown]
              :then
              (println "pressed!" keyname)]})
           (reduce o/add-rule (o/->session)))
      (o/insert "thiskey" ::pressed-key ::keydown)
      (o/fire-rules)
      (o/fire-rules)
      (o/query-all)))