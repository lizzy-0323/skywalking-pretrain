package main

import (
	"log"
	"os"
	"runtime/pprof"
	"time"
)

func fibonacci(n int) int {
	if n <= 1 {
		return n
	}
	return fibonacci(n-1) + fibonacci(n-2)
}

func main() {
	f, err := os.Create("cpu.pprof")
	if err != nil {
		log.Fatal("could not create CPU profile: ", err)
	}
	defer f.Close()

	if err := pprof.StartCPUProfile(f); err != nil {
		log.Fatal("could not start CPU profile: ", err)
	}
	defer pprof.StopCPUProfile()

	log.Println("Starting compute fibonacci...")
	start := time.Now()

	for i := 0; i < 5; i++ {
		result := fibonacci(45)
		log.Printf("Time: %d, Result: %d\n", i+1, result)
	}

	log.Printf("Complete, Time Cost: %v\n", time.Since(start))
}
