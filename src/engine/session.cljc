(ns engine.session
  (:require
   [clojure.spec.alpha :as s]
   [engine.esse :as esse]
   [odoyle.rules :as o]))

(defonce session* (atom {}))

(defn rules-debugger-wrap-fn [rule]
  (o/wrap-rule rule
               {:what
                (fn [f session new-fact old-fact]
                  ;; (println :what (:name rule) new-fact old-fact)
                  (f session new-fact old-fact))
                :when
                (fn [f session match]
                  ;; (println :when (:name rule) match)
                  (f session match))
                :then
                (fn [f session match]
                  (when (#{} (:name rule))
                    (println "firing" (:name rule)))
                  ;; (println :then (:name rule) match)
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
     [esse-id ::esse/frame-index leva-frame-index]]

    ::move-player
    [:what
     [keyname ::pressed-key ::keydown]
     [esse-id ::esse/pos-x pos-x {:then false}]
     [esse-id ::esse/pos-y pos-y {:then false}]
     [esse-id ::esse/frame-index frame-index {:then false}]
     :then
     (o/insert! esse-id
                {::esse/pos-x (case keyname :left (dec pos-x) :right (inc pos-x) pos-x)
                 ::esse/pos-y (case keyname :up (dec pos-y) :down (inc pos-y) pos-y)
                 ::esse/frame-index (case keyname :down 0 :left 12 :right 24 :up 36 0)})
     (o/retract! keyname ::pressed-key)]

    ::update-player-pos
    [:what
     [esse-id ::esse/pos-x pos-x]
     [esse-id ::esse/pos-y pos-y]
     [esse-id ::esse/x x {:then false}]
     [esse-id ::esse/y y {:then false}]
     :then
     (let [grid 64]
       (o/insert! esse-id
                  {::esse/x (* grid pos-x)
                   ::esse/y (* grid pos-y)}))]

    ::sprite-esse
    [:what
     [esse-id ::esse/pos-x pos-x]
     [esse-id ::esse/pos-y pos-y]
     [esse-id ::esse/x x]
     [esse-id ::esse/y y]
     [esse-id ::esse/frame-index frame-index]
     [esse-id ::esse/current-sprite current-sprite]]

    ::load-image
    [:what
     [esse-id ::esse/image-to-load image-path]]

    ::loading-image
    [:what
     [esse-id ::esse/image-to-load image-path]
     [esse-id ::esse/loading-image true]
     :then
     (o/retract! esse-id ::esse/image-to-load)]}))

(def initial-session
  (-> (->> rules
           (map #'rules-debugger-wrap-fn)
           (reduce o/add-rule (o/->session)))
      ;; if it's inserted partially it will not hit the rule and facts will be discarded
      (o/insert :ubim 
                #::esse{:pos-x 4 :pos-y 4 :x 0 :y 0 
                        :frame-index 0
                        :image-to-load "char0.png"})))

(o/query-all @session*)

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
