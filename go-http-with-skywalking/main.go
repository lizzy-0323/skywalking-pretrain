package main

import (
	"net/http"
	"time"

	"go.uber.org/zap"
)

func main() {
	logger, _ := zap.NewProduction()
	http.HandleFunc("/hello", func(writer http.ResponseWriter, request *http.Request) {
		logger.Info("Getting /hello")
		time.Sleep(1 * time.Second)
		writer.Write([]byte("Hello World"))
	})
	logger.Info("Starting at 8000")
	err := http.ListenAndServe(":8000", nil)
	if err != nil {
		panic(err)
	}
}
