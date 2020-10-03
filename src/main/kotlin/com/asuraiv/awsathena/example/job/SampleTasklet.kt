package com.asuraiv.awsathena.example.job

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.batch.core.StepContribution
import org.springframework.batch.core.scope.context.ChunkContext
import org.springframework.batch.core.step.tasklet.Tasklet
import org.springframework.batch.repeat.RepeatStatus
import org.springframework.stereotype.Component
import software.amazon.awssdk.services.athena.AthenaClient
import software.amazon.awssdk.services.athena.model.*

@Component
class SampleTasklet(
    val athenaClient: AthenaClient
) : Tasklet {

    val log: Logger = LoggerFactory.getLogger(SampleTasklet::class.java)

    override fun execute(contribution: StepContribution, chunkContext: ChunkContext): RepeatStatus? {

        log.info("Execute athena sample tasklet")

        val queryExecutionId = submitSampleQuery()

        waitForQueryToComplete(queryExecutionId)

        return RepeatStatus.FINISHED
    }

    private fun submitSampleQuery(): String {

        val queryExecutionContext = QueryExecutionContext.builder()
            .database("bis_data")
            .build()

        val resultConfiguration = ResultConfiguration.builder()
            .outputLocation("s3://bisdata-test/query-results")
            .build()

        val startQueryExecutionRequest = StartQueryExecutionRequest.builder()
            .queryString("SELECT * FROM bus_locations LIMIT 10;")
            .queryExecutionContext(queryExecutionContext)
            .resultConfiguration(resultConfiguration)
            .build()


        return athenaClient.startQueryExecution(startQueryExecutionRequest).queryExecutionId()
    }

    private fun waitForQueryToComplete(queryExecutionId: String) {

        val getQueryExecutionRequest = GetQueryExecutionRequest.builder()
            .queryExecutionId(queryExecutionId)
            .build()

        var getQueryExecutionResponse: GetQueryExecutionResponse

        var isRunning = true

        while(isRunning) {

            getQueryExecutionResponse = athenaClient.getQueryExecution(getQueryExecutionRequest)

            val state = getQueryExecutionResponse.queryExecution().status().state().toString()

            when(state) {

                QueryExecutionState.FAILED.toString() -> throw RuntimeException("Query Failed to run with Error Message: ${getQueryExecutionResponse
                    .queryExecution().status().stateChangeReason()}")

                QueryExecutionState.CANCELLED.toString() -> throw RuntimeException("Query was canceled.")

                QueryExecutionState.SUCCEEDED.toString() -> isRunning = false

                else -> Thread.sleep(2000L)
            }

            log.info("Current state is $state")
        }
    }
}