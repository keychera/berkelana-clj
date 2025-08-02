(ns engine.session
  (:require
   [clojure.spec.alpha :as s]
   [engine.esse :as esse]
   [odoyle.rules :as o]
   [engine.shader :as shader]))

(defonce session* (atom nil))

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
                  (when (#{::sprite-ready} (:name rule))
                    (println "firing" (:name rule)))
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

    ::mouse
    [:what
     [::mouse ::x x]
     [::mouse ::y y]]

    ::pressed-key
    [:what
     [keyname ::pressed-key ::keyup]]

    ::leva-spritesheet
    [:what
     [::leva-spritesheet ::crop? crop?]
     [::leva-spritesheet ::frame leva-frame-index]
     [esse-id ::esse/frame-index _ {:then false}]
     :then
     (o/insert! esse-id ::esse/frame-index leva-frame-index)]

    ::move-player
    [:what
     [::time ::delta delta-time]
     [keyname ::pressed-key ::keydown]
     [:ubim ::esse/pos-x pos-x {:then false}]
     [esse-id ::esse/pos-y pos-y {:then false}]
     [esse-id ::esse/frame-index frame-index {:then false}]
     [esse-id ::esse/move-delay move-delay {:then false}]
     [esse-id ::esse/move-duration move-duration {:then false}]
     :when
     (<= move-delay 0)
     (#{:left :right :up :down} keyname)
     :then
     (o/insert! esse-id
                {::esse/prev-x pos-x
                 ::esse/prev-y pos-y
                 ::esse/pos-x (case keyname :left (dec pos-x) :right (inc pos-x) pos-x)
                 ::esse/pos-y (case keyname :up (dec pos-y) :down (inc pos-y) pos-y)
                 ::esse/move-delay move-duration})]

    ::move-delay
    [:what
     [::time ::delta delta-time]
     [:ubim ::esse/move-delay move-delay {:then false}]
     :when (> move-delay 0)
     :then
     (o/insert! :ubim {::esse/move-delay (- move-delay delta-time)})]

    ::move-animation
    [:what
     [::time ::delta delta-time]
     [keyname ::pressed-key ::keydown]
     [:ubim ::esse/anim-tick anim-tick {:then false}]
     [esse-id ::esse/anim-elapsed-ms anim-elapsed-ms {:then false}]
     [esse-id ::esse/frame-index frame-index {:then false}]
     :when
     (#{:left :right :up :down} keyname)
     :then
     (if (> anim-elapsed-ms 100) ;; need to think more about the 'mutable' and the constant
       (o/insert! esse-id {::esse/anim-tick (inc anim-tick) ::esse/anim-elapsed-ms 0})
       (o/insert! esse-id {::esse/anim-elapsed-ms (+ anim-elapsed-ms delta-time)}))
     (let [pingpong (case (mod anim-tick 4) 0 -1 1 0 2 1 3 0)]
       (o/insert! esse-id {::esse/frame-index (- (case keyname :down 1 :left 13 :right 25 :up 37 1) pingpong)}))]

    ::animate-pos
    [:what
     [:ubim ::esse/pos-x px]
     [esse-id ::esse/pos-y py]
     [esse-id ::esse/prev-x sx]
     [esse-id ::esse/prev-y sy]
     [esse-id ::esse/move-delay move-delay]
     [esse-id ::esse/move-duration move-duration {:then false}]
     :then
     (let [t (- 1.0 (/ move-delay move-duration))
           ease-fn #(Math/pow % 2) grid 64
           x (+ sx (* (- px sx) (ease-fn t)))
           y (+ sy (* (- py sy) (ease-fn t)))]
       (o/insert! esse-id
                  {::esse/x (* grid x)
                   ::esse/y (* grid y)}))]

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
     (o/retract! esse-id ::esse/sprite-from-asset)
     (o/insert! esse-id ::esse/current-sprite image)]

    ::load-image
    [:what
     [asset-id ::asset-image-to-load image-path]]

    ::loading-image
    [:what
     [asset-id ::asset-image-to-load image-path]
     [asset-id ::image-loading? true]
     :then
     (o/retract! asset-id ::asset-image-to-load)]

    ::image-asset
    [:what
     [asset-id ::image-asset image]]}))

(defonce ^:devonly previous-rules (atom nil))

(defn init-session [session]
  (let [all-rules (concat rules shader/rules)
        session (if (some? session)
                  (->> @previous-rules ;; devonly : refresh rules without resetting facts
                       (map :name)
                       (reduce o/remove-rule session))
                  (o/->session))]
    (reset! previous-rules all-rules)
    (-> (->> all-rules
             (map #'rules-debugger-wrap-fn)
             (reduce o/add-rule session))
        (o/insert ::leva-spritesheet ::crop? true)
        (o/insert :asset/char0 ::asset-image-to-load "char0.png")
        ;; if esse attributes are inserted partially it will not hit the rule and facts will be discarded
        (o/insert :john
                  #::esse{::shader/shader-to-load shader/->hati :move-duration 100 :move-delay 0 
                          :prev-x 4 :prev-y 4 :pos-x 4 :pos-y 4 :x 0 :y 0 :frame-index 0})
        (o/insert :ubim
                  #::esse{:sprite-from-asset :asset/char0 :move-duration 100
                          #_mutable
                          :prev-x 4 :prev-y 4 :pos-x 4 :pos-y 4 :x 0 :y 0
                          :frame-index 0 :move-delay 0 :anim-tick 0 :anim-elapsed-ms 0}))))

;; specs
(s/def ::total number?)
(s/def ::delta number?)

(s/def ::width number?)
(s/def ::height number?)

(s/def ::x number?)
(s/def ::y number?)

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