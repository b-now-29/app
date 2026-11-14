import { Text, VStack } from "@seed-design/react";
import { useLoaderData, useSearchParams } from "react-router";
import NicknameText from "../components/NicknameText";
import SelectField from "../components/SelectField";
import type { RankingLoaderData } from "./rankingLoader";

export default function RankingPage() {
  const { category, categories, ranking } =
    useLoaderData() as RankingLoaderData;
  const [, setSearchParams] = useSearchParams();
  const { entries, me } = ranking;

  return (
    <VStack gap="x4" alignItems="stretch">
      <Text as="h1" textStyle="screenTitle">
        랭킹
      </Text>
      <SelectField
        label="카테고리"
        value={category}
        options={[
          { value: "", label: "전체" },
          ...categories.map((item) => ({ value: item.slug, label: item.name })),
        ]}
        onChange={(value) =>
          setSearchParams(value === "" ? {} : { category: value })
        }
      />
      {me !== null && (
        <Text as="p" textStyle="t5Bold">
          {me.rank === null
            ? "아직 순위가 없어요"
            : `내 순위: ${me.rank}위 (${me.score}문제)`}
        </Text>
      )}
      {entries.length === 0 ? (
        <Text as="p" textStyle="t5Regular">
          아직 랭킹이 없어요
        </Text>
      ) : (
        <table>
          <thead>
            <tr>
              <th>등수</th>
              <th>닉네임</th>
              <th>점수</th>
            </tr>
          </thead>
          <tbody>
            {entries.map((entry) => (
              <tr
                key={entry.rank}
                aria-current={entry.rank === me?.rank ? "true" : undefined}
                style={entry.rank === me?.rank ? highlightStyle : undefined}
              >
                <td>{entry.rank}</td>
                <td>
                  <NicknameText member={entry} />
                </td>
                <td>{entry.score}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </VStack>
  );
}

const highlightStyle = { fontWeight: "bold" } as const;
