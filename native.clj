(defmulti task first)

(defmethod task "run"
  [_]
  (println "hello")
  (require '[engine.start-dev])
  ((resolve 'engine.start-dev/start)))

(task *command-line-args*)